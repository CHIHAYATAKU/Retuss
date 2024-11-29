package io.github.morichan.retuss.model;

import java.util.ArrayList;
import java.util.List;

public class UmlModel {
    private static final UmlModel instance = new UmlModel();
    private String currentPlantUml;
    private final List<UmlChangeListener> changeListeners = new ArrayList<>();

    private UmlModel() {
        this.currentPlantUml = "";
    }

    public static UmlModel getInstance() {
        return instance;
    }

    public interface UmlChangeListener {
        void onUmlChanged(String newPlantUml);
    }

    public void setPlantUml(String plantUml) {
        this.currentPlantUml = plantUml;
        notifyUmlChanged();
    }

    public String getPlantUml() {
        return currentPlantUml;
    }

    public void addChangeListener(UmlChangeListener listener) {
        changeListeners.add(listener);
    }

    private void notifyUmlChanged() {
        for (UmlChangeListener listener : changeListeners) {
            listener.onUmlChanged(currentPlantUml);
        }
    }
}