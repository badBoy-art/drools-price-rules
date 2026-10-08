/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.TypeMismatchException
 *  org.springframework.dao.DataAccessException
 *  org.springframework.http.HttpStatus
 *  org.springframework.http.ResponseEntity
 *  org.springframework.http.converter.HttpMessageNotReadableException
 *  org.springframework.web.bind.annotation.ExceptionHandler
 *  org.springframework.web.bind.annotation.RestControllerAdvice
 */
package example.rules;

import example.rules.ApiModels;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(value={IllegalArgumentException.class})
    public ResponseEntity<ApiModels.Problem> badRequest(IllegalArgumentException e) {
        return this.error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(value={HttpMessageNotReadableException.class})
    public ResponseEntity<ApiModels.Problem> badJson(HttpMessageNotReadableException e) {
        return this.error(HttpStatus.BAD_REQUEST, "JSON\u7ed3\u6784\u3001\u679a\u4e3e\u503c\u6216\u6570\u5b57\u683c\u5f0f\u65e0\u6548");
    }

    @ExceptionHandler(value={NoSuchElementException.class})
    public ResponseEntity<ApiModels.Problem> notFound(NoSuchElementException e) {
        return this.error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(value={TypeMismatchException.class})
    public ResponseEntity<ApiModels.Problem> invalidParameter(TypeMismatchException e) {
        return this.error(HttpStatus.BAD_REQUEST, "\u8def\u5f84\u6216\u67e5\u8be2\u53c2\u6570\u7c7b\u578b\u65e0\u6548");
    }

    @ExceptionHandler(value={IllegalStateException.class, DataAccessException.class})
    public ResponseEntity<ApiModels.Problem> serverError(Exception e) {
        LOG.error("\u89c4\u5219\u6267\u884c\u6216\u5b58\u50a8\u5931\u8d25", (Throwable)e);
        return this.error(HttpStatus.INTERNAL_SERVER_ERROR, "\u89c4\u5219\u6267\u884c\u6216\u5b58\u50a8\u5931\u8d25");
    }

    private ResponseEntity<ApiModels.Problem> error(HttpStatus status, String detail) {
        return ResponseEntity.<ApiModels.Problem>status(status).body(new ApiModels.Problem("about:blank", status.getReasonPhrase(), status.value(), detail));
    }
}
