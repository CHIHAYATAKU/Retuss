package io.github.morichan.retuss.model.uml;

import io.github.morichan.fescue.feature.parameter.Parameter;
import io.github.morichan.fescue.feature.type.Type;

import java.util.ArrayList;

public class Message {
    private String name = "";
    private ArrayList<Parameter> parameterList = new ArrayList<>();
    private Type replyType;
    private OccurencceSpecification messageEnd;

    public Message(String name, OccurencceSpecification messageEnd) {
        this.name = name;
        this.messageEnd = messageEnd;
    }

}
