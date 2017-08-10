package cc.blynk.server.core.model.web.product.events;

import cc.blynk.server.core.model.web.product.EventReceiver;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 08.06.17.
 */
public abstract class SystemEvent extends Event {

    public SystemEvent(int id, String name, String description,
                       boolean isNotificationsEnabled, String eventCode,
                       EventReceiver[] emailNotifications,
                       EventReceiver[] pushNotifications,
                       EventReceiver[] smsNotifications) {
        super(id, name, description, isNotificationsEnabled, eventCode, emailNotifications, pushNotifications, smsNotifications);
    }

}
