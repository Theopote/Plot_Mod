package com.plot.api.plugin;

/**
 * 仓库凭证接口
 */
public interface IRepositoryCredentials {
    /**
     * 获取用户名
     * @return 用户名
     */
    String getUsername();

    /**
     * 获取密码
     * @return 密码
     */
    String getPassword();

    /**
     * 获取访问令牌
     * @return 访问令牌
     */
    String getAccessToken();

    /**
     * 获取凭证类型
     * @return 凭证类型
     */
    CredentialType getType();

    /**
     * 验证凭证是否有效
     * @return 是否有效
     */
    boolean validate();

    /**
     * 凭证类型枚举
     */
    enum CredentialType {
        /** 用户名密码 */
        USERNAME_PASSWORD,
        /** 访问令牌 */
        ACCESS_TOKEN,
        /** 匿名访问 */
        ANONYMOUS
    }
}
