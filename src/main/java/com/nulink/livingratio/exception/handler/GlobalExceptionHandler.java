package com.nulink.livingratio.exception.handler;

import com.nulink.livingratio.exception.BadRequestException;
import com.nulink.livingratio.utils.ThrowableUtil;
import com.nulink.livingratio.vo.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(Throwable.class)
    public BaseResponse<ApiError> handleException(Throwable e){
        log.error(ThrowableUtil.getStackTrace(e));
        return buildResponseEntity(ApiError.error(e.getMessage()));
    }

	@ExceptionHandler(value = BadRequestException.class)
	public BaseResponse<ApiError> badRequestException(BadRequestException e) {
        log.error(ThrowableUtil.getStackTrace(e));
        return buildResponseEntity(ApiError.error(e.getStatus(),e.getMessage()));
	}

    /**
     * 统一返回
     */
    private BaseResponse buildResponseEntity(ApiError apiError) {
        return BaseResponse.failed(apiError.getMessage(), null);
    }
}
