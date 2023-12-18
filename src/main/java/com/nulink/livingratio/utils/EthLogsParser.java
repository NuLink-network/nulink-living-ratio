package com.nulink.livingratio.utils;

import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author  kris.wang
 */
public class EthLogsParser {

    public static BigInteger hexToBigInteger(String strHex) {
        if (strHex.length() > 2) {
            if (strHex.charAt(0) == '0' && (strHex.charAt(1) == 'X' || strHex.charAt(1) == 'x')) {
                strHex = strHex.substring(2);
            }
            BigInteger bigInteger = new BigInteger(strHex, 16);
            return bigInteger;
        }
        return null;
    }

    public static String hexToAddress(String strHex) {
        if (strHex.length() > 42) {
            if (strHex.charAt(0) == '0' && (strHex.charAt(1) == 'X' || strHex.charAt(1) == 'x')) {
                strHex = strHex.substring(2);
            }
            strHex = strHex.substring(24);
            return "0x" + strHex;
        }
        return null;
    }

    private static EventValues staticExtractEventParameters(Event event, Log log) {
        final List<String> topics = log.getTopics();
        String encodedEventSignature = EventEncoder.encode(event);
        if (topics == null || topics.size() == 0 || !topics.get(0).equals(encodedEventSignature)) {
            return null;
        }

        List<Type> indexedValues = new ArrayList<>();
        List<Type> nonIndexedValues =
                FunctionReturnDecoder.decode(log.getData(), event.getNonIndexedParameters());

        List<TypeReference<Type>> indexedParameters = event.getIndexedParameters();
        for (int i = 0; i < indexedParameters.size(); i++) {
            Type value =
                    FunctionReturnDecoder.decodeIndexedValue(
                            topics.get(i + 1), indexedParameters.get(i));
            indexedValues.add(value);
        }
        return new EventValues(indexedValues, nonIndexedValues);
    }

    public static List<EventValues> extractEventParameters(
            Event event, EthLog ethLog) {
        return ethLog.getLogs().stream()
                .map(log -> extractEventParameters(event, (Log)log.get()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static EventValues extractEventParameters(Event event, Log log) {
        return staticExtractEventParameters(event, log);
    }
}
