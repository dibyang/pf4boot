package net.xdob.pf4boot.modal;


import java.io.Serializable;


public class PluginError implements Serializable {

  private static final long serialVersionUID = -153864270345999338L;

  private String pluginId;

  private String errorMessage;

  private String errorDetail;

  public static PluginError of(String pluginId, String errorMessage, String errorDetail) {
    PluginError pluginError = new PluginError();
    pluginError.pluginId = pluginId;
    pluginError.errorMessage = errorMessage;
    pluginError.errorDetail = errorDetail;
    return pluginError;
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
