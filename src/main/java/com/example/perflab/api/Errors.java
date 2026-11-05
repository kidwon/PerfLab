package com.example.perflab.api;
import java.util.Map;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice
public class Errors {
    @ExceptionHandler(EmptyResultDataAccessException.class) public ResponseEntity<?> missing(Exception e) {
        return response(404,"NOT_FOUND","记录不存在");
    }
    @ExceptionHandler( {
        IllegalArgumentException.class,ConstraintViolationException.class,MethodArgumentNotValidException.class,HandlerMethodValidationException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class
    }
    )
    public ResponseEntity<?> invalid(Exception e) {
        return response(400,"INVALID_INPUT","请求参数或 JSON 不合法，请检查取值范围");
    }
    @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<?> conflict(Exception e) {
        return response(409,"DATA_CONFLICT","商品不存在或数据违反约束");
    }
    @ExceptionHandler(Exception.class) public ResponseEntity<?> failure(Exception e) {
        if(e instanceof org.springframework.web.ErrorResponse error) return response(error.getStatusCode().value(),"HTTP_ERROR",error.getBody().getDetail() == null ? "请求错误" : error.getBody().getDetail());
        org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request failed",e);
        return response(500,"INTERNAL_ERROR","服务暂时不可用，请查看服务日志");
    }
    private ResponseEntity<?> response(int status,String code,String message) {
        return ResponseEntity.status(status).body(Map.of("code",code,"message",message));
    }
}
