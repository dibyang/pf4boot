package com.ls.pf4boot.spring.boot;


import java.io.Serializable;


public class PluginStartingError implements Serializable {

  private static final long serialVersionUID = -153864270345999338L;

  private String pluginId;

  private String errorMessage;

  private String errorDetail;

  public static PluginStartingError of(String pluginId, String errorMessage, String errorDetail) {
    PluginStartingError pluginStartingError = new PluginStartingError();
    pluginStartingError.pluginId = pluginId;
    pluginStartingError.errorMessage = errorMessage;
    pluginStartingError.errorDetail = errorDetail;
    return pluginStartingError;
  }

  public String getPluginId() {
    return pluginId;
  }

  public void setPluginId(String pluginId) {
    this.pluginId = pluginId;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public void setErrorDetail(String errorDetail) {
    this.errorDetail = errorDetail;
  }

}
