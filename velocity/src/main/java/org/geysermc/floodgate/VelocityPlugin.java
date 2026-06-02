/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Floodgate
 */

package org.geysermc.floodgate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import org.geysermc.floodgate.api.InstanceHolder;
import org.geysermc.floodgate.api.handshake.HandshakeHandlers;
import org.geysermc.floodgate.api.logger.FloodgateLogger;
import org.geysermc.floodgate.module.CommandModule;
import org.geysermc.floodgate.module.PluginMessageModule;
import org.geysermc.floodgate.module.ProxyCommonModule;
import org.geysermc.floodgate.module.VelocityAddonModule;
import org.geysermc.floodgate.module.VelocityListenerModule;
import org.geysermc.floodgate.module.VelocityPlatformModule;
import org.geysermc.floodgate.util.ReflectionUtils;
import org.geysermc.floodgate.util.VelocityBindingResolver;

public final class VelocityPlugin {
    private final FloodgatePlatform platform;
    private static HikariDataSource dataSource;

    public static HikariDataSource getDataSource() {
        return dataSource;
    }

    @Inject
    public VelocityPlugin(@DataDirectory Path dataDirectory, Injector guice) {
        ReflectionUtils.setPrefix("com.velocitypowered.proxy");

        long ctm = System.currentTimeMillis();
        Injector injector = guice.createChildInjector(
                new ProxyCommonModule(dataDirectory),
                new VelocityPlatformModule(guice)
        );

        platform = injector.getInstance(FloodgatePlatform.class);

        long endCtm = System.currentTimeMillis();
        injector.getInstance(FloodgateLogger.class)
                .translatedInfo("floodgate.core.finish", endCtm - ctm);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        platform.enable(
                new CommandModule(),
                new VelocityListenerModule(),
                new VelocityAddonModule(),
                new PluginMessageModule()
        );


        if (platform.isForceUserName()) {
            dataSource = new HikariDataSource();

            dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
            dataSource.setJdbcUrl(platform.getMysqlurl());
            dataSource.setUsername(platform.getMysqluser());
            dataSource.setPassword(platform.getMysqlpass());
            try {
                dataSource.getConnection().close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }

            // Resolve PC-PE bindings (canonical=PC) at handshake so Floodgate builds its state under
            // the canonical UUID, keeping skins / isFloodgatePlayer / forms consistent after merge.
            HandshakeHandlers handshakeHandlers = InstanceHolder.getHandshakeHandlers();
            if (handshakeHandlers != null) {
                handshakeHandlers.addHandshakeHandler(new VelocityBindingResolver());
            }
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        platform.disable();
    }
}
