import { createApp } from 'vue'
import 'element-plus/dist/index.css'
import '../../src/styles/index.css'
import FaceSettingsPreview from './FaceSettingsPreview.vue'

// Isolated review entry. The production router and real enrollment API are unchanged.
createApp(FaceSettingsPreview).mount('#app')
