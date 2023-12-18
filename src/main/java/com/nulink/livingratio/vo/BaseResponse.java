package com.nulink.livingratio.vo;

import com.nulink.livingratio.utils.MessageUtils;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class BaseResponse<T> implements Serializable {

    private static final long serialVersionUID = -5229330081398026892L;

    private int code;

    private String msg;

    private T data;

    public BaseResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public BaseResponse(boolean success, String msg, T data) {
        if (success) {
            // 业务code，0 为成功
            this.code = 0;
        } else {
            this.code = 1;
        }
        this.msg = msg;
        this.data = data;
    }

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(true, MessageUtils.get("success"), data);
    }

    public static <T> BaseResponse<T> successWhenDataNotEmpty(T data) {
        if (isEmpty(data)) {
            return failed(MessageUtils.get("response.datanotfound"), data);
        } else {
            return new BaseResponse<>(true, "success", data);
        }
    }

    private static boolean isEmpty(Object data) {
        if (null == data) {
            return true;
        }
        if (data instanceof List) {
            return ((List) data).size() == 0;
        }
        return false;
    }


    public static <T> BaseResponse<T> failed(String message, T data) {
        return new BaseResponse<>(false, message, data);
    }
}
