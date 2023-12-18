package com.nulink.livingratio.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PorterRequestVO {
    @JsonProperty("result")
    private Result result;

    @JsonProperty("version")
    private String version;

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public static class Result {
        @JsonProperty("total")
        private int total;

        @JsonProperty("list")
        private List<String> list;

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public List<String> getList() {
            return list;
        }

        public void setList(List<String> list) {
            this.list = list;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "total=" + total +
                    ", list=" + list +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "PorterRequestVO{" +
                "result=" + result +
                ", version='" + version + '\'' +
                '}';
    }
}
