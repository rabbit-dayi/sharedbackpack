package com.sharedbackpack.protocol;

import net.minecraft.util.Identifier;

/** The only code shared by the server mod and the independent client addon. */
public final class BackpackProtocol {
    public static final int VERSION = 1;
    public static final int MAX_QUERY_LENGTH = 128;
    public static final int OPEN = 0;
    public static final int SEARCH = 1;
    public static final Identifier REQUEST = new Identifier("sharedbackpack", "request_v1");
    public static final Identifier STATE = new Identifier("sharedbackpack", "state_v1");
    public static final Identifier STATUS_REQUEST = new Identifier("sharedbackpack", "status_request_v1");
    public static final Identifier STATUS = new Identifier("sharedbackpack", "status_v1");

    private BackpackProtocol() {
    }
}
