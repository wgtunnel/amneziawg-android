package org.amnezia.awg.backend;

import java.util.Collection;

public interface KillSwitchHandler {
    void activateKillSwitch(Collection<String> allowedIps) throws Exception;
    void deactivateKillSwitch() throws Exception;
}
