import { useEffect, useState, type ReactNode } from 'react';
import {
  Sparkles, Mic, Cpu, Database, Zap, Search as SearchIcon, Wand2, Image as ImageIcon,
} from 'lucide-react';
import { AiConsole } from '../components/AiConsole';
import { useSeo } from '../hooks/useSeo';

interface AiStatus {
  configured: boolean;
  provider: string;
  model: string;
  grounded: boolean;
  capabilities: Record<string, boolean>;
}

const CAPABILITIES: { key: string; label: string; icon: ReactNode }[] = [
  { key: 'chat', label: 'Conversational shopping', icon: <Sparkles size={13} /> },
  { key: 'imageSearch', label: 'Photo search', icon: <ImageIcon size={13} /> },
  { key: 'voiceSearch', label: 'Voice search', icon: <Mic size={13} /> },
  { key: 'search', label: 'Whole-catalogue search', icon: <SearchIcon size={13} /> },
  { key: 'agents', label: '6 specialist agents', icon: <Wand2 size={13} /> },
  { key: 'grounded', label: 'Live stock & prices', icon: <Database size={13} /> },
];

/** Public AI page — anonymous shoppers get the same grounded engine. */
export default function Ai() {
  useSeo({
    title: 'AI shopping assistant',
    description:
      'Ask the ScottsTechX assistant to find products, compare prices and ' +
      'recommend sellers across the whole marketplace.',
  });

  // No PageHeader here. Its title and subtitle sat ABOVE the chat and ate
  // ~90px of vertical space on a phone before the conversation even started.
  // The same words now live inside the chat's own header and welcome panel,
  // so the card gets the full height of the screen.
  return (
    <AiConsole
      audience="buyer"
      screen="web-public-ai"
      title="AI shopper"
      subtitle="Ask anything about the store — no account needed. Tell the assistant what you need and your budget, and it searches the whole catalogue."
      fullHeight
    />
  );
}
