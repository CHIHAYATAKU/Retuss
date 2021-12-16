package io.github.morichan.retuss.model.uml;

import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;

import java.util.ArrayList;
import java.util.Objects;


public class Message {
    private String name = "";
    private ArrayList<Parameter> parameterList = new ArrayList<>();
    private Type replyType;
    private OccurenceSpecification messageEnd;
    private MessageSort messageSort = MessageSort.synchCall;


    public Message(String name, OccurenceSpecification messageEnd) {
        this.name = name;
        this.messageEnd = messageEnd;
    }

    public String getName() {
        return name;
    }

    public ArrayList<Parameter> getParameterList() {
        return parameterList;
    }

    public void setParameterList(ArrayList<Parameter> parameterList) {
        this.parameterList = parameterList;
    }

    public Type getReplyType() {
        return replyType;
    }

    public OccurenceSpecification getMessageEnd() {
        return messageEnd;
    }

    public MessageSort getMessageSort() { return messageSort; }

    public void setMessageSort(MessageSort messageSort) { this.messageSort = messageSort; }


    public String getSignature() {
        StringBuilder sb = new StringBuilder();

        if (this.messageSort == MessageSort.synchCall) {
            sb.append(name);
            // 引数
            sb.append("(");
            sb.append(parameterListToSignateru());
            sb.append(")");
            // 戻り値の型
            if(Objects.nonNull(replyType)) {
                sb.append(String.format(" : %s\n", replyType.getName()));
            }

        } else if (this.messageSort == MessageSort.createMessage) {
            if (parameterList.size() == 0) {
                sb.append("<<create>>");
            } else {
                sb.append("create");
                // 引数
                sb.append("(");
                sb.append(parameterListToSignateru());
                sb.append(")");
                // 戻り値の型
                if(Objects.nonNull(replyType)) {
                    sb.append(String.format(" : %s\n", replyType.getName()));
                }
            }
        }

        return sb.toString();
    }

    private String parameterListToSignateru() {
        StringBuilder sb = new StringBuilder();

        if(parameterList.size() > 0) {
            for(Parameter parameter : parameterList) {
                sb.append(parameter.getName());
                try {
                    sb.append(String.format(" : %s", parameter.getType()));
                } catch (Exception e) {

                }
                if(parameterList.indexOf(parameter) < parameterList.size() - 1) {
                    sb.append(", ");
                }
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return getSignature();
    }
}
