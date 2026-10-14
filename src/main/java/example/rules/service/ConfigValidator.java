package example.rules.service;

/** 商品审核配置与请求共用的基本校验。 */
public final class ConfigValidator {
    private ConfigValidator() {}
    public static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
    public static void identifier(String value) {
        require(value != null && value.matches("[A-Za-z0-9_:-]{1,64}"), "品类标识需为1到64位字母、数字、下划线、冒号或短横线");
    }
}
