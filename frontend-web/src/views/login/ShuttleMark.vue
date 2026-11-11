<script setup lang="ts">
defineProps<{ monochrome?: boolean }>()
</script>

<template>
  <svg viewBox="0 0 48 48" fill="none" aria-hidden="true">
    <circle cx="7" cy="40" r="5" :fill="monochrome ? 'currentColor' : '#60bf69'" />
    <path d="M8 32C9 22 13 10 18 5C21 1 27 3 24 9C21 17 14 28 8 34Z" :fill="monochrome ? 'currentColor' : '#249e91'" />
    <path d="M12 34C18 22 25 10 31 7C36 4 40 8 36 14C30 22 20 31 12 35Z" :fill="monochrome ? 'currentColor' : '#168e7f'" />
    <path d="M15 36C26 26 34 16 40 15C46 13 49 18 44 22C37 29 26 34 15 37Z" :fill="monochrome ? 'currentColor' : '#2ba969'" />
  </svg>
</template>
