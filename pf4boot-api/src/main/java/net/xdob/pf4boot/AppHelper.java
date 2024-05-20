package net.xdob.pf4boot;


import java.util.Optional;

public enum AppHelper {
  i;
  public static final String APP_HOME = "app.home";
  public static final String USER_DIR = "user.dir";
  public static final String APP_NAME = "app.name";
  public static final String APP_VERSION = "app.version";

  public String getAppHome(){
    String appHome = System.getProperty(APP_HOME, System.getProperty(USER_DIR));
    this.setAppHome(appHome);
    return appHome;
  }

  public void setAppHome(String appHome){
    System.setProperty(APP_HOME, appHome);
  }

  public Optional<String> getAppVersion(){
    return Optional.ofNullable(System.getProperty(APP_VERSION));
  }

  public String getAppVersion(String defVersion){
    Optional<String> appVersion = getAppVersion();
    if(!appVersion.isPresent()){
      setAppName(defVersion);
    }
    return appVersion.orElse(defVersion);
  }
  public void setAppVersion(String appName){
    System.setProperty(APP_VERSION, appName);
  }

  public Optional<String> getAppName(){
    return Optional.ofNullable(System.getProperty(APP_NAME));
  }

  public String getAppName(String defName){
    Optional<String> appName = getAppName();
    if(!appName.isPresent()){
      setAppName(defName);
    }
    return appName.orElse(defName);
  }
  public void setAppName(String appName){
    System.setProperty(APP_NAME, appName);
  }

}
