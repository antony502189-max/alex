import React from 'react';

import { ChatScreenContent, type ChatScreenProps } from '../components/chat/ChatScreenContent';

export function ChatScreen(props: ChatScreenProps) {
  return <ChatScreenContent {...props} />;
}
