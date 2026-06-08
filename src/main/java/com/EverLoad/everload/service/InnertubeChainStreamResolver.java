package com.EverLoad.everload.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Second-line resolver: walks {@link YtMusicClient#MAIN_CLIENT} followed by
 * {@link YtMusicClient#STREAM_FALLBACK_CLIENTS}, calling {@code /player}
 * with no PO token at all. These client identities occasionally hand back
 * plain (unsigned) URLs on their own — and crucially, a client that doesn't
 * support sign-in (ANDROID_VR) sometimes reports a video playable when
 * WEB_REMIX claims LOGIN_REQUIRED, so the chain is worth walking even after
 * the primary client already answered.
 *
 * <p>No PO token is minted here — this resolver exists precisely so stream
 * resolution still has a chance when {@link BotguardStreamResolver} can't
 * run (binary missing/misconfigured) or returns nothing useful.
 */
@Component
@Order(20)
public class InnertubeChainStreamResolver implements YtStreamResolver {

    private final YtMusicInnertubeClient client;

    public InnertubeChainStreamResolver(YtMusicInnertubeClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "innertube-chain";
    }

    @Override
    public YtStreamResolution resolve(String videoId) {
        List<YtMusicClient> chain = new ArrayList<>();
        chain.add(YtMusicClient.MAIN_CLIENT);
        for (YtMusicClient fallback : YtMusicClient.STREAM_FALLBACK_CLIENTS) {
            if (!chain.contains(fallback)) {
                chain.add(fallback);
            }
        }

        YtStreamResolution lastResult = YtStreamResolution.failure(YtPlayabilityStatus.UNKNOWN, "cadena de clientes vacía");
        for (YtMusicClient identity : chain) {
            JsonNode playerResponse;
            try {
                playerResponse = client.player(identity, videoId, null, null);
            } catch (YtMusicTransportException e) {
                lastResult = YtStreamResolution.failure(YtPlayabilityStatus.UNKNOWN,
                        identity.clientName() + ": " + e.getMessage());
                continue;
            }
            YtStreamResolution result = YtPlayerResponseInterpreter.interpret(playerResponse, identity, name());
            if (result.isSuccess()) {
                return result;
            }
            lastResult = result;
        }
        return lastResult;
    }
}
