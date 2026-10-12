# Filter reference samples

Four neutral, 16-level grayscale crops contain only the Unread filter label/badge and surrounding background. `video-selected` is from the supplied video at 5.5 seconds; `photo-unselected` is from the supplied PNG. Scaled variants use the app's 0.4 scale for a 2400-pixel window. No conversation names, avatars, messages, or Snap content are included.

Format: width height label-left label-top label-right label-bottom expected-state; RGB hex palette; one hex palette index per pixel. These isolated crops calibrate the dark-theme selection probe, not full-screen recognition or Android interactions. Accessibility bounds are approximations and must be verified on the Pixel. Light themes stay unknown unless native selection semantics are available.
