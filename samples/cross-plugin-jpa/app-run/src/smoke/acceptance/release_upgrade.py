"""Verify preserved 3.2.0 business binaries against the final Maven consumer."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java', required=True)
    parser.add_argument('--port', type=int, default=17832)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    from verify import sha
    for name, digest in json.loads((root / 'sha256.json').read_text()).items():
        if sha(root / name) != digest:
            raise RuntimeError('Sealed input changed: ' + name)
    if '1.8.0' not in subprocess.check_output([args.java, '-version'], stderr=subprocess.STDOUT).decode():
        raise RuntimeError('Java 8 is required')
    reports = root / 'reports'
    reports.mkdir(exist_ok=True)
    work = root / 'release-upgrade-work'
    work.mkdir()
    old = root / 'release-3.2.0'
    for directory in ['plugins', 'config']:
        shutil.copytree(str(old / directory), str(work / directory))
    inputs = {p.name: sha(p) for p in (old / 'plugins').glob('*.zip')}
    if len(inputs) != 4:
        raise RuntimeError('Expected four original 3.2.0 plugins')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    result = {'status': 'RUNNING', 'oldPlugins': inputs, 'checks': []}
    serial = [0]

    def request(path, post=False, expected=200):
        serial[0] += 1
        req = urllib.request.Request('http://127.0.0.1:%d%s' % (args.port, path),
            data=b'{}' if post else None, headers={'Content-Type': 'application/json',
            'X-PF4Boot-Admin-Token': 'sample-token', 'X-Idempotency-Key': 'release-%d' % serial[0]})
        try:
            response = opener.open(req, timeout=120)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = json.load(response)
            if response.code != expected:
                raise urllib.error.HTTPError(req.full_url, response.code, str(body), None, None)
            return body

    def summary():
        return request('/api/sample/workflow/summary')

    def business(label):
        before = summary()
        query = urllib.parse.urlencode({'username': label, 'password': 'sample', 'bookName': label})
        after = request('/api/sample/workflow/place?' + query)
        if any(after[k] != before[k] + 1 for k in ['users', 'books', 'audits']):
            raise RuntimeError('Business commit failed: ' + label)
        request('/api/sample/workflow/place?' + query.replace(label, label + '-fail') + '&failAfterAudit=true', expected=500)
        rolled = summary()
        if rolled != dict(after, audits=after['audits'] + 1):
            raise RuntimeError('REQUIRES_NEW/outer rollback failed: ' + label)
        return rolled

    def host(label, libraries, check, strict=False):
        command = [args.java, '-Xmx768m', '-Dserver.address=127.0.0.1',
            '-Duser.home=' + str(work / 'home'), '-cp', str(libraries / '*'),
            'net.xdob.sample.host.CrossPluginJpaSampleHost', '--server.port=' + str(args.port),
            '--spring.config.location=' + (root / 'runtime/config/application.yml').as_uri(),
            '--management.endpoint.shutdown.enabled=true',
            '--management.endpoints.web.exposure.include=health,shutdown,pf4bootplugins',
            '--spring.pf4boot.management.http.token=sample-token']
        if strict:
            command += ['--spring.pf4boot.production-profile-enabled=true']
        print('START release-' + label, flush=True)
        log_path = reports / ('release-' + label + '.log')
        with log_path.open('wb') as log:
            process = subprocess.Popen(command, cwd=str(work), stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.time() + 120
                while True:
                    if strict and 'Plugin package checksum file not found:' in log_path.read_text(encoding='utf-8', errors='replace'):
                        if process.poll() is not None:
                            break
                        try:
                            request('/actuator/health')
                            request('/api/sample/workflow/summary', expected=404)
                            request('/actuator/shutdown', True)
                            process.wait(timeout=30)
                        except (urllib.error.URLError, OSError):
                            if time.time() > deadline:
                                raise RuntimeError('Strict host did not complete rejection')
                            time.sleep(0.5)
                            continue
                        break
                    if process.poll() is not None or time.time() > deadline:
                        raise RuntimeError('Host not ready: ' + label)
                    try:
                        summary()
                        if strict:
                            raise RuntimeError('Strict mode accepted unsigned old plugins')
                        request('/api/sample/unrelated/health')
                        break
                    except (urllib.error.URLError, OSError):
                        time.sleep(0.5)
                if not strict:
                    check()
                    request('/actuator/shutdown', True)
                    process.wait(timeout=30)
                if not strict and process.returncode != 0:
                    raise RuntimeError('Host shutdown failed: ' + label)
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=10)
        result['checks'].append({'name': label, 'status': 'PASSED'})
        print('PASSED release-' + label, flush=True)

    try:
        saved = {}
        host('3.2.0-before-upgrade', old / 'lib', lambda: saved.update(business('before-upgrade')))
        backup = root / 'release-3.2.0-data-backup'
        shutil.copytree(str(work / 'work'), str(backup))

        def upgraded():
            if summary() != saved:
                raise RuntimeError('Upgrade did not preserve existing business data')
            business('after-upgrade')
            for action, count in [('restart', 20), ('reload', 10)]:
                for unused in range(count):
                    before = summary()
                    response = request('/pf4boot/admin/plugins/sample-workflow/' + action, True)
                    if response.get('code') != 'OK' or summary() != before:
                        raise RuntimeError('Old binary lifecycle failed: ' + action + str(response))
            result['restarts'] = 20
            result['reloads'] = 10

        host('3.3.0-maven-upgrade', root / 'consumer-lib', upgraded)
        # 将新版数据保留在旁，回滚只恢复一致的旧版备份，绝不让旧宿主碰新版数据。
        (work / 'work').rename(root / 'release-3.3.0-data-retained')
        shutil.copytree(str(backup), str(work / 'work'))

        def rollback():
            if summary() != saved:
                raise RuntimeError('Rollback did not restore the pre-upgrade snapshot')
            business('after-rollback')

        host('3.2.0-backup-rollback', old / 'lib', rollback)
        host('3.3.0-strict-rejection', root / 'consumer-lib', None, strict=True)
        if any(sha(work / 'plugins' / name) != digest for name, digest in inputs.items()):
            raise RuntimeError('An old plugin ZIP was changed')
        result['status'] = 'PASSED'
    except Exception as error:
        result.update(status='FAILED', failure=str(error))
        raise
    finally:
        (reports / 'release-upgrade.json').write_text(json.dumps(result, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
