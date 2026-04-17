package com.admin.common.context;

public final class ActorContextHolder {

    private static final ThreadLocal<ActorContext> HOLDER = new ThreadLocal<>();

    private ActorContextHolder() {
    }

    public static void set(ActorContext actorContext) {
        HOLDER.set(actorContext);
    }

    public static ActorContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
