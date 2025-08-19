package org.amnezia.awg.config;

import java.util.Optional;

public record DnsSettings (Boolean dohEnabled, Optional<String> dohUrl){}
