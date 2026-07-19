package com.xkball.x3dmap.api.client;

import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@NonNullByDefault
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface X3dMapPlugin {
}
