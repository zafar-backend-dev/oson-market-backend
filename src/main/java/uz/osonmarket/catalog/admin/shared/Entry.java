package uz.osonmarket.catalog.admin.shared;

import java.util.UUID;

public record Entry<T>(UUID id, T data) {}
