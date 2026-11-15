package com.sharedbackpack.protocol;

import net.minecraft.util.Identifier;

/** Versioned virtual catalog; vanilla packets still own the deposit and player inventory slots. */
public final class DashboardProtocol {
    public static final int VERSION = 1;
    public static final int MAX_TEXT = 128;
    public static final int CHUNK_SIZE = 32;
    public static final int REFRESH = 0, TAKE = 1, SWITCH = 2, CREATE_BOX = 3,
            PREFERENCES = 4, UPGRADE = 5, DEPOSIT_CURSOR = 6;
    public static final Identifier STATE = new Identifier("sharedbackpack", "dashboard_state_v1");
    public static final Identifier REQUEST = new Identifier("sharedbackpack", "dashboard_request_v1");

    private DashboardProtocol() { }
}
