package io.smstunnel.spring.boot;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Explains a missing API key in the "APPLICATION FAILED TO START" report. */
class SmsTunnelApiKeyMissingFailureAnalyzer extends AbstractFailureAnalyzer<SmsTunnelApiKeyMissingException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SmsTunnelApiKeyMissingException cause) {
        return new FailureAnalysis(
                "The SMSTunnel starter needs an API key, and smstunnel.api-key is not set.",
                "Create a key in your SMSTunnel dashboard (https://dashboard.smstunnel.io/apps: Apps, Add an app), put"
                        + " it in an environment variable, and add smstunnel.api-key=${SMSTUNNEL_API_KEY} to"
                        + " application.properties. To run without SMSTunnel, for example in tests, set"
                        + " smstunnel.enabled=false.",
                cause);
    }
}
