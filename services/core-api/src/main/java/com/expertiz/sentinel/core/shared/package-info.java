/**
 * Noyau partagé, ouvert à tous les modules : Problem Details, pagination et tri, corrélation, horloge,
 * constantes de permissions, type {@code AuditEvent}, empreintes HMAC (spec §7.1).
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.expertiz.sentinel.core.shared;

import org.springframework.modulith.ApplicationModule;
