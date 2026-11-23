/*
 * BedWarsX - Copyright (c) 2026 ASHRAF ELHENAWY (ItzRealAshraf). All rights reserved.
 * Copying, re-selling, re-branding or removing the credits is not allowed.
 */
package dev.bedwarsx;

import java.util.LinkedHashSet;
import java.util.UUID;

public class Party {
    public UUID leader;
    public final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    public int size() { return members.size(); }
}
