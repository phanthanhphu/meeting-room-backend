package org.bsl.meetingroom.exception;

import org.bsl.meetingroom.dto.Dtos.ApiError;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiError> handleApp(AppException ex){
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getCode(),ex.getMessage(),LocalDateTime.now(),List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex){
        List<String> details=ex.getBindingResult().getFieldErrors().stream().map(e->e.getField()+": "+e.getDefaultMessage()).toList();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR","Dữ liệu không hợp lệ",LocalDateTime.now(),details));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("DATA_CONFLICT","Dữ liệu bị trùng hoặc vi phạm ràng buộc",LocalDateTime.now(),List.of()));
    }


    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONCURRENT_UPDATE","Dữ liệu vừa được thay đổi bởi thao tác khác. Vui lòng tải lại và thử lại.",LocalDateTime.now(),List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception ex){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR","Có lỗi hệ thống xảy ra",LocalDateTime.now(),List.of()));
    }
}
