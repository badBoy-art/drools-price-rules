package example.rules.controller;
import example.rules.result.ApiModels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiModels.Problem> conflict(OptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * 将业务配置或入参错误映射为HTTP 400。
     *
     * @param e 待转换的JSON数据。
     */
    @ExceptionHandler(value = {IllegalArgumentException.class})
    public ResponseEntity<ApiModels.Problem> badRequest(IllegalArgumentException e) {
        return this.error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 将JSON反序列化错误映射为HTTP 400。
     *
     * @param e 待转换的JSON数据。
     */
    @ExceptionHandler(value = {HttpMessageNotReadableException.class})
    public ResponseEntity<ApiModels.Problem> badJson(HttpMessageNotReadableException e) {
        return this.error(
                HttpStatus.BAD_REQUEST,
                "JSON\u7ed3\u6784\u3001\u679a\u4e3e\u503c\u6216\u6570\u5b57\u683c\u5f0f\u65e0\u6548");
    }

    /**
     * 将未找到的规则映射为HTTP 404。
     *
     * @param e 待转换的JSON数据。
     */
    @ExceptionHandler(value = {NoSuchElementException.class})
    public ResponseEntity<ApiModels.Problem> notFound(NoSuchElementException e) {
        return this.error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * 将无效路径或查询参数映射为HTTP 400。
     *
     * @param e 待转换的JSON数据。
     */
    @ExceptionHandler(value = {TypeMismatchException.class})
    public ResponseEntity<ApiModels.Problem> invalidParameter(TypeMismatchException e) {
        return this.error(
                HttpStatus.BAD_REQUEST,
                "\u8def\u5f84\u6216\u67e5\u8be2\u53c2\u6570\u7c7b\u578b\u65e0\u6548");
    }

    /**
     * 记录服务端异常并返回HTTP 500。
     *
     * @param e 待转换的JSON数据。
     */
    @ExceptionHandler(value = {IllegalStateException.class, DataAccessException.class})
    public ResponseEntity<ApiModels.Problem> serverError(Exception e) {
        LOG.error("\u89c4\u5219\u6267\u884c\u6216\u5b58\u50a8\u5931\u8d25", (Throwable) e);
        return this.error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "\u89c4\u5219\u6267\u884c\u6216\u5b58\u50a8\u5931\u8d25");
    }

    /**
     * 构造统一格式的HTTP错误响应。
     *
     * @param status HTTP响应状态。
     * @param detail 返回给调用方的错误详情。
     */
    private ResponseEntity<ApiModels.Problem> error(HttpStatus status, String detail) {
        return ResponseEntity.<ApiModels.Problem>status(status)
                .body(
                        new ApiModels.Problem(
                                "about:blank", status.getReasonPhrase(), status.value(), detail));
    }
}
