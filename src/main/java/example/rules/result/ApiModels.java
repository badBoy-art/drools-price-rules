package example.rules.result;

/** HTTP错误响应。 */
public final class ApiModels {
    private ApiModels() {}
    public static final class Problem {
        public final String type;
        public final String title;
        public final int status;
        public final String detail;
        public Problem(String type, String title, int status, String detail) {
            this.type = type; this.title = title; this.status = status; this.detail = detail;
        }
    }
}
