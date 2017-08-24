package cc.blynk.server.core.model.widgets.web;

import cc.blynk.server.core.model.widgets.OnePinWidget;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 24.08.17.
 */

public class WebLabel extends OnePinWidget {

    @Override
    public String getModeType() {
        return "in";
    }

    @Override
    public int getPrice() {
        return 0;
    }
}
