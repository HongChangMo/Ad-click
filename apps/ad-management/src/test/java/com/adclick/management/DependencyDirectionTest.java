package com.adclick.management;

import org.junit.jupiter.api.Test;

class DependencyDirectionTest {

    @Test
    void adManagement_should_not_depend_on_adClick() {
        try {
            Class.forName("com.adclick.click.application.ClickFacade");
            throw new AssertionError("ad-management must not depend on ad-click");
        } catch (ClassNotFoundException e) {
            // 정상: ad-click 클래스가 클래스패스에 없어야 함
        }
    }
}
