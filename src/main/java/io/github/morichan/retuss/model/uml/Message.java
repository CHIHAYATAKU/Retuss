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

    public Message(String name, OccurenceSpecification messageEnd) {
        this.name = name;
        this.messageEnd = messageEnd;
    }

    public ArrayList<Parameter> getParameterList() {
        return parameterList;
    }

    public String getName() {
        return name;
    }

    public Type getReplyType() {
        return replyType;
    }

    public OccurenceSpecification getMessageEnd() {
        return messageEnd;
    }

    public void setParameterList(ArrayList<Parameter> parameterList) {
        this.parameterList = parameterList;
    }

    public String getSignature() {
        StringBuilder sb = new StringBuilder();

        sb.append(name);
        // 引数
        sb.append("(");
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
        sb.append(")");


        // 戻り値の型
        if(Objects.nonNull(replyType)) {
            sb.append(String.format(" : %s\n", replyType.getName()));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return getSignature();
    }
}
