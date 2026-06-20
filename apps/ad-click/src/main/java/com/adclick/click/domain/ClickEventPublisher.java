package com.adclick.click.domain;

public interface ClickEventPublisher {

    void publish(ClickEvent event);
}
