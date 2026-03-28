import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { AuthSecurityEvent } from "../../types";
import {
  formatSecurityEventDeviceMeta,
  formatSecurityEventNetworkMeta,
  formatSecurityEventTitle
} from "./profilePresentation";

type ProfileSecurityEventsSectionProps = {
  events: AuthSecurityEvent[];
};

export function ProfileSecurityEventsSection({
  events
}: ProfileSecurityEventsSectionProps) {
  return (
    <SectionCard title="Security events">
      <Text style={styles.metaText}>Recent authentication and device activity.</Text>
      <View style={styles.list}>
        {events.length === 0 ? (
          <Text style={styles.metaText}>No recent security events.</Text>
        ) : (
          events.slice(0, 8).map((event) => (
            <View key={event.eventId} style={styles.card}>
              <Text style={styles.title}>{formatSecurityEventTitle(event)}</Text>
              <Text style={styles.metaText}>{formatSecurityEventDeviceMeta(event)}</Text>
              {formatSecurityEventNetworkMeta(event) ? (
                <Text style={styles.metaText}>{formatSecurityEventNetworkMeta(event)}</Text>
              ) : null}
              {event.details ? <Text style={styles.metaText}>{event.details}</Text> : null}
              <Text style={styles.metaText}>{new Date(event.createdAt).toLocaleString()}</Text>
            </View>
          ))
        )}
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  list: {
    gap: appSpacing.sm + 2,
    marginTop: appSpacing.sm + 2
  },
  card: {
    backgroundColor: "#f8fafc",
    borderRadius: appRadii.md,
    gap: 4,
    padding: appSpacing.md
  },
  title: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  metaText: {
    color: appColors.textSecondary,
    fontSize: 12,
    lineHeight: 18
  }
});
