package software.wings.beans;

import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by peeyushaggarwal on 11/28/16.
 */
@Entity(value = "delegates", noClassnameStored = true)
public class Delegate extends Base {
  @NotEmpty private String accountId;
  private Status status;
  private String ip;
  private String hostName;
  private long lastHeartBeat;
  private String version;
  @Transient private boolean doUpgrade;

  /**
   * Getter for property 'accountId'.
   *
   * @return Value for property 'accountId'.
   */
  public String getAccountId() {
    return accountId;
  }

  /**
   * Setter for property 'accountId'.
   *
   * @param accountId Value to set for property 'accountId'.
   */
  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  /**
   * Getter for property 'status'.
   *
   * @return Value for property 'status'.
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Setter for property 'status'.
   *
   * @param status Value to set for property 'status'.
   */
  public void setStatus(Status status) {
    this.status = status;
  }

  /**
   * Getter for property 'ip'.
   *
   * @return Value for property 'ip'.
   */
  public String getIp() {
    return ip;
  }

  /**
   * Setter for property 'ip'.
   *
   * @param ip Value to set for property 'ip'.
   */
  public void setIp(String ip) {
    this.ip = ip;
  }

  /**
   * Getter for property 'hostName'.
   *
   * @return Value for property 'hostName'.
   */
  public String getHostName() {
    return hostName;
  }

  /**
   * Setter for property 'hostName'.
   *
   * @param hostName Value to set for property 'hostName'.
   */
  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  /**
   * Getter for property 'lastHeartBeat'.
   *
   * @return Value for property 'lastHeartBeat'.
   */
  public long getLastHeartBeat() {
    return lastHeartBeat;
  }

  /**
   * Setter for property 'lastHeartBeat'.
   *
   * @param lastHeartBeat Value to set for property 'lastHeartBeat'.
   */
  public void setLastHeartBeat(long lastHeartBeat) {
    this.lastHeartBeat = lastHeartBeat;
  }

  /**
   * Getter for property 'version'.
   *
   * @return Value for property 'version'.
   */
  public String getVersion() {
    return version;
  }

  /**
   * Setter for property 'version'.
   *
   * @param version Value to set for property 'version'.
   */
  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * Getter for property 'doUpgrade'.
   *
   * @return Value for property 'doUpgrade'.
   */
  public boolean isDoUpgrade() {
    return doUpgrade;
  }

  /**
   * Setter for property 'doUpgrade'.
   *
   * @param doUpgrade Value to set for property 'doUpgrade'.
   */
  public void setDoUpgrade(boolean doUpgrade) {
    this.doUpgrade = doUpgrade;
  }

  public enum Status { ENABLED, DISABLED, DISCONNECTED, UPGRADING }

  public static final class Builder {
    private String accountId;
    private Status status;
    private String ip;
    private String hostName;
    private long lastHeartBeat;
    private String version;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;

    private Builder() {}

    public static Builder aDelegate() {
      return new Builder();
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder withStatus(Status status) {
      this.status = status;
      return this;
    }

    public Builder withIp(String ip) {
      this.ip = ip;
      return this;
    }

    public Builder withHostName(String hostName) {
      this.hostName = hostName;
      return this;
    }

    public Builder withLastHeartBeat(long lastHeartBeat) {
      this.lastHeartBeat = lastHeartBeat;
      return this;
    }

    public Builder withVersion(String version) {
      this.version = version;
      return this;
    }

    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder but() {
      return aDelegate()
          .withAccountId(accountId)
          .withStatus(status)
          .withIp(ip)
          .withHostName(hostName)
          .withLastHeartBeat(lastHeartBeat)
          .withVersion(version)
          .withUuid(uuid)
          .withAppId(appId)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt);
    }

    public Delegate build() {
      Delegate delegate = new Delegate();
      delegate.setAccountId(accountId);
      delegate.setStatus(status);
      delegate.setIp(ip);
      delegate.setHostName(hostName);
      delegate.setLastHeartBeat(lastHeartBeat);
      delegate.setVersion(version);
      delegate.setUuid(uuid);
      delegate.setAppId(appId);
      delegate.setCreatedBy(createdBy);
      delegate.setCreatedAt(createdAt);
      delegate.setLastUpdatedBy(lastUpdatedBy);
      delegate.setLastUpdatedAt(lastUpdatedAt);
      return delegate;
    }
  }
}
