import { useCallback, useMemo, useState } from "react";
import { api } from "../../services/api";
import { deviceLocation } from "../../services/deviceLocation";
import type {
  MessageAttachment,
  MessageContactCard,
  MessageLiveLocation,
  MessageLocation,
  StickerPack
} from "../../types";

type UseChatStructuredComposerParams = {
  setError: (value: string | null) => void;
  token: string;
};

export function useChatStructuredComposer({
  setError,
  token
}: UseChatStructuredComposerParams) {
  const [showPollComposer, setShowPollComposer] = useState(false);
  const [showLocationComposer, setShowLocationComposer] = useState(false);
  const [showContactComposer, setShowContactComposer] = useState(false);
  const [showGifPicker, setShowGifPicker] = useState(false);
  const [showStickerPicker, setShowStickerPicker] = useState(false);
  const [recentGifs, setRecentGifs] = useState<MessageAttachment[]>([]);
  const [stickerPacks, setStickerPacks] = useState<StickerPack[]>([]);
  const [pollQuestion, setPollQuestion] = useState("");
  const [pollOptions, setPollOptions] = useState<string[]>(["", ""]);
  const [pollMultipleChoice, setPollMultipleChoice] = useState(false);
  const [locationLatitude, setLocationLatitude] = useState("");
  const [locationLongitude, setLocationLongitude] = useState("");
  const [locationTitle, setLocationTitle] = useState("");
  const [locationAddress, setLocationAddress] = useState("");
  const [liveLocationEnabled, setLiveLocationEnabled] = useState(false);
  const [liveLocationPeriodMinutes, setLiveLocationPeriodMinutes] = useState("15");
  const [contactFirstName, setContactFirstName] = useState("");
  const [contactLastName, setContactLastName] = useState("");
  const [contactPhoneNumber, setContactPhoneNumber] = useState("");
  const [contactUserId, setContactUserId] = useState("");
  const [loadingRecentGifs, setLoadingRecentGifs] = useState(false);
  const [resolvingDeviceLocation, setResolvingDeviceLocation] = useState(false);
  const [loadingStickerPacks, setLoadingStickerPacks] = useState(false);

  const parsedLocation = useMemo(() => {
    const latitude = Number.parseFloat(locationLatitude);
    const longitude = Number.parseFloat(locationLongitude);
    if (Number.isNaN(latitude) || Number.isNaN(longitude)) {
      return null;
    }
    return {
      latitude,
      longitude,
      title: locationTitle.trim() || null,
      address: locationAddress.trim() || null
    } satisfies MessageLocation;
  }, [locationAddress, locationLatitude, locationLongitude, locationTitle]);

  const parsedLiveLocation = useMemo(() => {
    const baseLocation = parsedLocation;
    const livePeriodMinutes = Number.parseInt(liveLocationPeriodMinutes, 10);
    if (
      !baseLocation ||
      Number.isNaN(livePeriodMinutes) ||
      livePeriodMinutes < 1 ||
      livePeriodMinutes > 24 * 60
    ) {
      return null;
    }

    return {
      ...baseLocation,
      active: true,
      expiresAt: null,
      lastUpdatedAt: null,
      livePeriodSeconds: livePeriodMinutes * 60,
      stoppedAt: null
    } satisfies MessageLiveLocation;
  }, [liveLocationPeriodMinutes, parsedLocation]);

  const preparedContactCard = useMemo(() => {
    const firstName = contactFirstName.trim();
    const lastName = contactLastName.trim();
    const phoneNumber = contactPhoneNumber.trim();
    const userId = contactUserId.trim();
    if (!firstName && !phoneNumber && !lastName && !userId) {
      return null;
    }
    return {
      firstName: firstName || null,
      lastName: lastName || null,
      phoneNumber: phoneNumber || null,
      userId: userId || null,
      vcard: null
    } satisfies MessageContactCard;
  }, [contactFirstName, contactLastName, contactPhoneNumber, contactUserId]);

  const canSendLocation = Boolean(
    parsedLocation &&
      parsedLocation.latitude >= -90 &&
      parsedLocation.latitude <= 90 &&
      parsedLocation.longitude >= -180 &&
      parsedLocation.longitude <= 180
  );

  const canSendLiveLocation = Boolean(
    parsedLiveLocation &&
      parsedLiveLocation.latitude >= -90 &&
      parsedLiveLocation.latitude <= 90 &&
      parsedLiveLocation.longitude >= -180 &&
      parsedLiveLocation.longitude <= 180
  );

  const canSendContact = Boolean(
    preparedContactCard &&
      (preparedContactCard.firstName || preparedContactCard.phoneNumber)
  );

  const activeStructuredMessageType: "LOCATION" | "LIVE_LOCATION" | "CONTACT_CARD" | null = showLocationComposer
    ? liveLocationEnabled
      ? "LIVE_LOCATION"
      : "LOCATION"
    : showContactComposer
      ? "CONTACT_CARD"
      : null;

  const closeRichMediaPickers = useCallback(() => {
    setShowGifPicker(false);
    setShowStickerPicker(false);
  }, []);

  const hideStructuredComposerPanels = useCallback(() => {
    setShowPollComposer(false);
    setShowLocationComposer(false);
    setShowContactComposer(false);
  }, []);

  const resetPollComposer = useCallback(() => {
    setShowPollComposer(false);
    setPollQuestion("");
    setPollOptions(["", ""]);
    setPollMultipleChoice(false);
  }, []);

  const resetLocationComposer = useCallback(() => {
    setShowLocationComposer(false);
    setLocationLatitude("");
    setLocationLongitude("");
    setLocationTitle("");
    setLocationAddress("");
    setLiveLocationEnabled(false);
    setLiveLocationPeriodMinutes("15");
    setResolvingDeviceLocation(false);
  }, []);

  const handleUseCurrentLocation = useCallback(async () => {
    setResolvingDeviceLocation(true);
    setError(null);
    try {
      const snapshot = await deviceLocation.getCurrentPosition();
      setLocationLatitude(snapshot.latitude.toFixed(6));
      setLocationLongitude(snapshot.longitude.toFixed(6));
      setLocationAddress(snapshot.address ?? "");
      setLocationTitle((current) => current.trim() || "Current location");
    } catch (locationError) {
      setError(
        locationError instanceof Error ? locationError.message : "Unable to read device location"
      );
    } finally {
      setResolvingDeviceLocation(false);
    }
  }, [setError]);

  const resetContactComposer = useCallback(() => {
    setShowContactComposer(false);
    setContactFirstName("");
    setContactLastName("");
    setContactPhoneNumber("");
    setContactUserId("");
  }, []);

  const resetStructuredMessageInputs = useCallback(() => {
    resetPollComposer();
    resetLocationComposer();
    resetContactComposer();
  }, [resetContactComposer, resetLocationComposer, resetPollComposer]);

  const resetStructuredComposerState = useCallback(() => {
    resetStructuredMessageInputs();
    closeRichMediaPickers();
  }, [closeRichMediaPickers, resetStructuredMessageInputs]);

  const handleToggleStickerPicker = useCallback(async () => {
    const nextVisible = !showStickerPicker;
    if (nextVisible) {
      resetStructuredMessageInputs();
      setShowGifPicker(false);
    }
    setShowStickerPicker(nextVisible);
    if (!nextVisible || stickerPacks.length > 0 || loadingStickerPacks) {
      return;
    }

    setLoadingStickerPacks(true);
    setError(null);
    try {
      const packs = await api.getStickerPacks(token);
      setStickerPacks(packs);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load stickers");
    } finally {
      setLoadingStickerPacks(false);
    }
  }, [
    loadingStickerPacks,
    resetStructuredMessageInputs,
    setError,
    showStickerPicker,
    stickerPacks.length,
    token
  ]);

  const handleToggleGifPicker = useCallback(async () => {
    const nextVisible = !showGifPicker;
    if (nextVisible) {
      resetStructuredMessageInputs();
      setShowStickerPicker(false);
    }
    setShowGifPicker(nextVisible);
    if (!nextVisible || recentGifs.length > 0 || loadingRecentGifs) {
      return;
    }

    setLoadingRecentGifs(true);
    setError(null);
    try {
      const gifs = await api.getRecentGifs(token);
      setRecentGifs(gifs);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load recent GIFs");
    } finally {
      setLoadingRecentGifs(false);
    }
  }, [
    loadingRecentGifs,
    recentGifs.length,
    resetStructuredMessageInputs,
    setError,
    showGifPicker,
    token
  ]);

  const handleTogglePollComposer = useCallback(() => {
    const nextVisible = !showPollComposer;
    if (nextVisible) {
      resetLocationComposer();
      resetContactComposer();
      closeRichMediaPickers();
    }
    setShowPollComposer(nextVisible);
  }, [closeRichMediaPickers, resetContactComposer, resetLocationComposer, showPollComposer]);

  const handleToggleLocationComposer = useCallback(() => {
    const nextVisible = !showLocationComposer;
    if (nextVisible) {
      resetPollComposer();
      resetContactComposer();
      closeRichMediaPickers();
      if (!locationLatitude.trim() || !locationLongitude.trim()) {
        void handleUseCurrentLocation();
      }
    }
    setShowLocationComposer(nextVisible);
  }, [
    closeRichMediaPickers,
    handleUseCurrentLocation,
    locationLatitude,
    locationLongitude,
    resetContactComposer,
    resetPollComposer,
    showLocationComposer
  ]);

  const handleToggleContactComposer = useCallback(() => {
    const nextVisible = !showContactComposer;
    if (nextVisible) {
      resetPollComposer();
      resetLocationComposer();
      closeRichMediaPickers();
    }
    setShowContactComposer(nextVisible);
  }, [closeRichMediaPickers, resetLocationComposer, resetPollComposer, showContactComposer]);

  const updatePollOption = useCallback((index: number, value: string) => {
    setPollOptions((current) =>
      current.map((option, currentIndex) =>
        currentIndex === index ? value : option
      )
    );
  }, []);

  const addPollOption = useCallback(() => {
    setPollOptions((current) =>
      current.length >= 10 ? current : [...current, ""]
    );
  }, []);

  const removePollOption = useCallback((index: number) => {
    setPollOptions((current) =>
      current.length <= 2
        ? current
        : current.filter((_, currentIndex) => currentIndex !== index)
    );
  }, []);

  return {
    activeStructuredMessageType,
    addPollOption,
    canSendContact,
    canSendLocation,
    closeRichMediaPickers,
    contactFirstName,
    contactLastName,
    contactPhoneNumber,
    contactUserId,
    handleToggleContactComposer,
    handleToggleGifPicker,
    handleToggleLocationComposer,
    handleTogglePollComposer,
    handleToggleStickerPicker,
    handleUseCurrentLocation,
    hideStructuredComposerPanels,
    loadingRecentGifs,
    loadingStickerPacks,
    locationAddress,
    locationLatitude,
    locationLongitude,
    locationTitle,
    liveLocationEnabled,
    liveLocationPeriodMinutes,
    parsedLocation,
    parsedLiveLocation,
    pollMultipleChoice,
    pollOptions,
    pollQuestion,
    preparedContactCard,
    recentGifs,
    resetContactComposer,
    resetLocationComposer,
    resetPollComposer,
    resetStructuredComposerState,
    resetStructuredMessageInputs,
    resolvingDeviceLocation,
    setContactFirstName,
    setContactLastName,
    setContactPhoneNumber,
    setContactUserId,
    setLiveLocationEnabled,
    setLiveLocationPeriodMinutes,
    setLocationAddress,
    setLocationLatitude,
    setLocationLongitude,
    setLocationTitle,
    setPollMultipleChoice,
    setPollQuestion,
    setShowGifPicker,
    showContactComposer,
    showGifPicker,
    showLocationComposer,
    showPollComposer,
    showStickerPicker,
    stickerPacks,
    updatePollOption,
    removePollOption,
    canSendLiveLocation
  };
}
