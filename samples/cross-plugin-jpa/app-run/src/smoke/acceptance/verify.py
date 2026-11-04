"""Run existing Java smoke/JUnit workflows on an isolated Java 8 runtime."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import time
import urllib.request


def sha(path):
    with path.open('rb') as stream:
        return hashlib.sha256(stream.read()).hexdigest()


def request(port, path, post=False):
    req = urllib.request.Request('http://127.0.0.1:%d%s' % (port, path),
        data=b'{}' if post else None,
        headers={'X-PF4Boot-Admin-Token': 'sample-token', 'Content-Type': 'application/json'})
    # 回环验收不得继承 Windows 系统代理或远端 HTTP_PROXY。
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(req, timeout=120 if post else 3) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java', default='java')
    parser.add_argument('--port', type=int, default=17811)
    parser.add_argument('--seal', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    seal_path = root / 'sha256.json'
    if args.seal:
        files = {str(p.relative_to(root)).replace('\\', '/'): sha(p)
                 for p in sorted(root.rglob('*')) if p.is_file() and p != seal_path}
        seal_path.write_text(json.dumps(files, indent=2), encoding='utf-8')
        print('SEALED_FILES=%d' % len(files), flush=True)
        return
    for name, digest in json.loads(seal_path.read_text(encoding='utf-8')).items():
        if sha(root / name) != digest:
            raise RuntimeError('Artifact hash mismatch: ' + name)
    reports = root / 'reports'
    reports.mkdir(exist_ok=True)
    env = os.environ.copy()
    env['JAVA_TOOL_OPTIONS'] = '-Xmx768m -Dserver.address=127.0.0.1'
    version = subprocess.check_output([args.java, '-version'], stderr=subprocess.STDOUT).decode('utf-8', 'replace')
    if '1.8.0' not in version:
        raise RuntimeError('A real Java 8 JVM is required: ' + version)
    result = {'status': 'RUNNING', 'system': platform.platform(), 'java': version,
              'sealSha256': sha(seal_path), 'checks': []}

    def run(name, command, cwd=root):
        print('START ' + name, flush=True)
        with (reports / (name + '.log')).open('wb') as log:
            completed = subprocess.run(command, cwd=str(cwd), env=env, stdout=log,
                                       stderr=subprocess.STDOUT, timeout=600)
        if completed.returncode:
            raise RuntimeError('%s exited %d; inspect %s.log' % (name, completed.returncode, name))
        check = {'name': name, 'status': 'PASSED'}
        if name.startswith('junit-'):
            matched = re.search(r'OK \((\d+) tests?\)', (reports / (name + '.log')).read_text(encoding='utf-8', errors='replace'))
            if not matched:
                raise RuntimeError('Missing JUnit count: ' + name)
            check['tests'] = int(matched.group(1))
        result['checks'].append(check)
        print('PASSED ' + name, flush=True)

    try:
        manifest = json.loads((root / 'classpath.json').read_text(encoding='utf-8'))
        if not manifest['modules'] or not manifest['expectedTests']:
            raise RuntimeError('Export requires completed framework test results')
        if not (root / 'runtime/repository/repository-index.json').is_file():
            raise RuntimeError('Offline repository input is missing')
        for revision in ['41a04ab', '81526a7']:
            if len(list((root / 'baselines' / revision).glob('*.zip'))) != 4:
                raise RuntimeError('Expected four unchanged old plugin zips: ' + revision)
        for module in manifest['modules']:
            cp = os.pathsep.join(str(root / p) for p in module['cp'])
            run('junit-' + module['name'], [args.java, '-cp', cp, 'org.junit.runner.JUnitCore'] + module['tests'])
        if sum(c.get('tests', 0) for c in result['checks']) != manifest['expectedTests']:
            raise RuntimeError('JUnit count differs from the completed Gradle build')
        for label, extra in [('default', []), ('production', ['--profile', 'production', '--config-location', 'config/application.yml,config/application-production.yml']),
                             ('gson', ['--config-location', 'config/application.yml,' + (root / 'application-gson.yml').as_uri()])]:
            command = [args.java]
            if label == 'gson':
                command += ['-Dsample.expected-json-converter=GsonHttpMessageConverter']
            command += ['-cp', str(root / 'smoke'), 'net.xdob.sample.smoke.RuntimeSmokeRunner',
                        '--runtime', str(root / 'runtime'), '--port', str(args.port),
                        '--result', str(reports / (label + '.json')), '--junit', str(reports / (label + '.xml'))] + extra
            run('smoke-' + label, command)
            if json.loads((reports / (label + '.json')).read_text())['status'] != 'PASSED':
                raise RuntimeError('Smoke failed: ' + label)
        for revision in ['41a04ab', '81526a7']:
            for production in [False, True]:
                name = 'legacy-' + revision + ('-production' if production else '-default')
                work = root / name
                work.mkdir()
                for directory in ['lib', 'config']:
                    shutil.copytree(str(root / 'runtime' / directory), str(work / directory))
                shutil.copytree(str(root / 'baselines' / revision), str(work / 'plugins'))
                command = [args.java, '-XX:SoftRefLRUPolicyMSPerMB=0', '-Dsample.cleanup-probe-enabled=true',
                    '-Duser.home=' + str(work / 'home'), '-cp', 'lib/*', 'net.xdob.sample.host.CrossPluginJpaSampleHost',
                    '--server.port=' + str(args.port), '--sample.lifecycle-probe-enabled=true',
                    '--spring.pf4boot.management.http.token=sample-token',
                    '--spring.config.location=config/application.yml' + (',config/application-production.yml' if production else '')]
                if production:
                    command += ['--spring.profiles.active=production']
                print('START ' + name, flush=True)
                with (reports / (name + '.log')).open('wb') as log:
                    process = subprocess.Popen(command, cwd=str(work), env=env, stdout=log, stderr=subprocess.STDOUT)
                    try:
                        deadline = time.time() + 120
                        while True:
                            if process.poll() is not None or time.time() > deadline:
                                raise RuntimeError('Legacy host not ready: ' + name)
                            try:
                                request(args.port, '/api/sample/workflow/summary')
                                request(args.port, '/api/sample/unrelated/health')
                                break
                            except Exception:
                                time.sleep(0.5)
                        lifecycle = request(args.port, '/api/sample/lifecycle-probe', True)
                        if any(lifecycle.get(k) != v for k, v in {'restarts': 20, 'reloads': 10, 'collectedLoaders': 10, 'failureRecovery': 'PASSED'}.items()):
                            raise RuntimeError('Legacy lifecycle failed: ' + name)
                        request(args.port, '/api/sample/workflow/summary')
                        result['checks'].append({'name': name, 'status': 'PASSED', 'lifecycle': lifecycle})
                        print('PASSED ' + name, flush=True)
                    finally:
                        process.terminate()
                        try:
                            process.wait(timeout=10)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait(timeout=10)
        result['status'] = 'PASSED'
    except Exception as error:
        result['status'] = 'FAILED'
        result['failure'] = str(error)
        raise
    finally:
        (reports / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
