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
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR","Invalid request data.",LocalDateTime.now(),details));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("DATA_CONFLICT","This request conflicts with existing data or violates a constraint.",LocalDateTime.now(),List.of()));
    }


    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONCURRENT_UPDATE","This information was updated by another user. Refresh and try again.",LocalDateTime.now(),List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception ex){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR","An unexpected server error occurred.",LocalDateTime.now(),List.of()));
    }
}
