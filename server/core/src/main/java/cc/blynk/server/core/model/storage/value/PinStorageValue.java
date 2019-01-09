package cc.blynk.server.core.model.storage.value;

import cc.blynk.server.core.model.storage.key.DeviceStorageKey;
import io.netty.channel.Channel;

import java.util.Collection;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 27/04/2018.
 *
 */
public abstract class PinStorageValue {

    public abstract String update(String value);

    public abstract Collection<String> values();

    public abstract String lastValue();

    public abstract void sendAppSync(Channel appChannel, int deviceId, DeviceStorageKey key);

}
