/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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

package org.geysermc.floodgate.util;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.geysermc.floodgate.VelocityPlugin;
import org.geysermc.floodgate.api.handshake.HandshakeData;
import org.geysermc.floodgate.api.handshake.HandshakeHandler;

/**
 * Resolves a Bedrock player's PC-PE binding at the handshake by reading FloodgateProfileAPI's
 * {@code pcpe_binding} table. When the binding's canonical side is PC, the Bedrock player is linked
 * to the PC account's UUID via {@link HandshakeData#setLinkedPlayer}, BEFORE the FloodgatePlayer is
 * built. Doing it here (rather than rewriting the GameProfile UUID later at GameProfileRequest) keeps
 * Floodgate's own per-player state — skin, {@code isFloodgatePlayer}, forms, device info — consistent
 * under the canonical UUID.
 *
 * <p>Only the PE&rarr;PC direction is handled here. The PE-canonical direction (a PC account adopting
 * the PE UUID) is left to FloodgateProfileAPI's proxy-side remap, because a Java player needs none of
 * Floodgate's Bedrock-specific state.
 *
 * <p>UUIDs are stored as {@code BINARY(16)} big-endian, identical to Floodgate's own LinkedPlayers
 * encoding, so the same bytes round-trip with FloodgateProfileAPI.
 */
public final class VelocityBindingResolver implements HandshakeHandler {

    @Override
    public void handle(HandshakeData data) {
        if (data.getBedrockData() == null || !data.isFloodgatePlayer()) {
            return;
        }
        HikariDataSource dataSource = VelocityPlugin.getDataSource();
        if (dataSource == null) {
            return;
        }

        UUID bedrockId = data.getJavaUniqueId();
        try (Connection connection = dataSource.getConnection()) {
            UUID pcUuid = canonicalPcUuid(connection, bedrockId);
            if (pcUuid == null) {
                // not bound, or canonical is PE (the proxy-side remap handles that direction)
                return;
            }
            String pcName = lookupName(connection, pcUuid);
            if (pcName == null) {
                pcName = data.getBedrockData().getUsername();
            }
            data.setLinkedPlayer(LinkedPlayer.of(pcName, pcUuid, bedrockId));
        } catch (SQLException exception) {
            // fail open: if we can't resolve right now, the player simply isn't merged this login
        }
    }

    /** The PC UUID this Bedrock account is bound to with {@code canonical = 'pc'}, else {@code null}. */
    private UUID canonicalPcUuid(Connection connection, UUID bedrockId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pc_uuid, canonical FROM pcpe_binding WHERE pe_uuid = ?")) {
            statement.setBytes(1, toBytes(bedrockId));
            try (ResultSet result = statement.executeQuery()) {
                if (result.next() && "pc".equalsIgnoreCase(result.getString("canonical"))) {
                    return fromBytes(result.getBytes("pc_uuid"));
                }
            }
        }
        return null;
    }

    private String lookupName(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM localprofile WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("name") : null;
            }
        }
    }

    private static byte[] toBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        ByteBuffer.wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits());
        return bytes;
    }

    private static UUID fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
