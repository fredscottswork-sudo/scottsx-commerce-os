/**
 * ScottsTechX — one source of truth for the marketplace category set.
 *
 * The home page showcase and the nav mega-menu both merge these 16
 * categories with the LIVE facet counts from the backend, so the grid is
 * always full and even (16 = 8×2 on desktop, 4×4 on phones) even when the
 * database has no products in a category yet — or no products at all.
 */
import type { ReactNode } from 'react';
import {
  Package, Shirt, Sparkle, Sofa, Dumbbell, ToyBrick, Car, HeartPulse,
  Gem, ShoppingBag, Apple, Factory, Smartphone, Laptop, Wheat, PawPrint, Cpu,
} from 'lucide-react';

export const STATIC_CATEGORIES: readonly string[] = [
  'Electronics',
  'Fashion',
  'Beauty',
  'Home & Living',
  'Sports',
  'Toys',
  'Automotive',
  'Health',
  'Jewelry',
  'Bags & Shoes',
  'Groceries',
  'Industrial',
  'Phones',
  'Computers',
  'Agriculture',
  'Pets',
];

const ICONS: Record<string, ReactNode> = {
  Electronics: <Cpu size={16} />,
  Fashion: <Shirt size={16} />,
  Beauty: <Sparkle size={16} />,
  'Home & Living': <Sofa size={16} />,
  Sports: <Dumbbell size={16} />,
  Toys: <ToyBrick size={16} />,
  Automotive: <Car size={16} />,
  Health: <HeartPulse size={16} />,
  Jewelry: <Gem size={16} />,
  'Bags & Shoes': <ShoppingBag size={16} />,
  Groceries: <Apple size={16} />,
  Industrial: <Factory size={16} />,
  Phones: <Smartphone size={16} />,
  Computers: <Laptop size={16} />,
  Agriculture: <Wheat size={16} />,
  Pets: <PawPrint size={16} />,
};

export function categoryIcon(name: string): ReactNode {
  return ICONS[name] ?? <Package size={16} />;
}

export const CATEGORY_IMAGES: Record<string, string> = {
  Electronics: '/cat/electronics.jpg',
  Fashion: '/cat/fashion.jpg',
  Beauty: '/cat/beauty.jpg',
  'Home & Living': '/cat/home-living.jpg',
  Sports: '/cat/sports.jpg',
  Toys: '/cat/toys.jpg',
  Automotive: '/cat/automotive.jpg',
  Health: '/cat/health.jpg',
  Jewelry: '/cat/jewelry.jpg',
  'Bags & Shoes': '/cat/bags-shoes.jpg',
  Groceries: '/cat/groceries.jpg',
  Industrial: '/cat/industrial.jpg',
  Phones: '/cat/phones.jpg',
  Computers: '/cat/computers.jpg',
  Agriculture: '/cat/agriculture.jpg',
  Pets: '/cat/pets.jpg',
};

/** Photo used as the tile background on the home category showcase. */
export function categoryImage(name: string): string {
  return CATEGORY_IMAGES[name] ?? '';
}

/** Static categories merged with live facet counts, always in the fixed order. */
export function mergedCategories(
  live: { name: string; count: number }[]
): { name: string; count: number }[] {
  const counts = new Map(live.map((c) => [c.name, c.count]));
  return STATIC_CATEGORIES.map((name) => ({ name, count: counts.get(name) ?? 0 }));
}
