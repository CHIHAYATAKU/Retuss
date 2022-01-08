package io.github.morichan.retuss.model.uml;

import io.github.morichan.fescue.feature.parameter.Parameter;

import java.util.ArrayList;
import java.util.List;

public class InteractionUse extends InteractionFragment {
    private String collaborationUse = "";
    private String interactionName = "";
    private List<Parameter> parameterList = new ArrayList<>();

    public InteractionUse(Lifeline lifeline, String interactionName) {
        super.setLifeline(lifeline);
        this.interactionName = interactionName;
    }

    public String getCollaborationUse() {
        return collaborationUse;
    }

    public String getInteractionName() {
        return interactionName;
    }

    public List<Parameter> getParameterList() {
        return parameterList;
    }

    public void setCollaborationUse(String collaborationUse) {
        this.collaborationUse = collaborationUse;
    }

    public String getSignature() {
        StringBuilder sb = new StringBuilder();
        if(collaborationUse.length() > 0) {
            sb.append(collaborationUse + ".");
        }
        sb.append(interactionName);
        // 引数
        sb.append("(");
        sb.append(parameterListToSignateru());
        sb.append(")");

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
}
