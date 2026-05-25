package com.xkball.x3dmap.api.client.map;

import com.xkball.xklib.api.gui.input.IKeyEvent;
import com.xkball.xklib.api.gui.input.IMouseButtonEvent;

public sealed interface WorldMapEvent permits WorldMapEvent.Input {

    sealed abstract class Input implements WorldMapEvent permits MouseClicked, MouseReleased, MouseDragged, MouseScrolled, KeyPressed {

        private boolean consumed;

        public boolean consumed() {
            return this.consumed;
        }

        public void consume() {
            this.consumed = true;
        }
    }

    final class MouseClicked extends Input {

        private final IMouseButtonEvent event;
        private final boolean doubleClick;

        public MouseClicked(IMouseButtonEvent event, boolean doubleClick) {
            this.event = event;
            this.doubleClick = doubleClick;
        }

        public IMouseButtonEvent event() {
            return this.event;
        }

        public boolean doubleClick() {
            return this.doubleClick;
        }
    }

    final class MouseReleased extends Input {

        private final IMouseButtonEvent event;

        public MouseReleased(IMouseButtonEvent event) {
            this.event = event;
        }

        public IMouseButtonEvent event() {
            return this.event;
        }
    }

    final class MouseDragged extends Input {

        private final IMouseButtonEvent event;
        private final double dx;
        private final double dy;

        public MouseDragged(IMouseButtonEvent event, double dx, double dy) {
            this.event = event;
            this.dx = dx;
            this.dy = dy;
        }

        public IMouseButtonEvent event() {
            return this.event;
        }

        public double dx() {
            return this.dx;
        }

        public double dy() {
            return this.dy;
        }
    }

    final class MouseScrolled extends Input {

        private final double x;
        private final double y;
        private final double scrollX;
        private final double scrollY;

        public MouseScrolled(double x, double y, double scrollX, double scrollY) {
            this.x = x;
            this.y = y;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
        }

        public double x() {
            return this.x;
        }

        public double y() {
            return this.y;
        }

        public double scrollX() {
            return this.scrollX;
        }

        public double scrollY() {
            return this.scrollY;
        }
    }

    final class KeyPressed extends Input {

        private final IKeyEvent event;

        public KeyPressed(IKeyEvent event) {
            this.event = event;
        }

        public IKeyEvent event() {
            return this.event;
        }
    }
}
