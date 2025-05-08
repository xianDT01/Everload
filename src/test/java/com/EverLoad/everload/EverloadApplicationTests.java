	package com.EverLoad.everload;

	import org.junit.jupiter.api.Test;
	import org.springframework.beans.factory.annotation.Autowired;
	import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
	import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
	import org.springframework.boot.test.context.SpringBootTest;
	import org.springframework.test.web.servlet.MockMvc;
	import org.springframework.test.web.servlet.ResultActions;

	import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
	import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
	import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

	@SpringBootTest
	@AutoConfigureMockMvc
	public class EverloadApplicationTests {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void contextLoads() {
		}

		@Test
		void searchYouTubeVideosShouldReturnOk() throws Exception {
			mockMvc.perform(get("/api/youtube/search")
							.param("query", "java"))
					.andExpect(status().isOk());

		}


		@Test
		void shouldReturnErrorWhenDownloadingFakeVideo() throws Exception {
			mockMvc.perform(get("/api/downloadVideo")
							.param("videoId", "invalidVideoId123")
							.param("resolution", "720"))
					.andExpect(status().is5xxServerError());
		}

		@Test
		void searchYouTubeReturnsOk() throws Exception {
			mockMvc.perform(get("/api/youtube/search")
							.param("query", "lofi"))
					.andExpect(status().isOk());
		}

		@Test
		void searchYouTubeWithoutQueryShouldFail() throws Exception {
			mockMvc.perform(get("/api/youtube/search"))
					.andExpect(status().isBadRequest());
		}

		@Test
		void youtubeSearchShouldReturnNonEmptyBody() throws Exception {
			ResultActions result = mockMvc.perform(get("/api/youtube/search")
							.param("query", "música"))
					.andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.containsString("items")));
		}
		@Test
		void downloadVideoWithoutVideoIdShouldFail() throws Exception {
			mockMvc.perform(get("/api/downloadVideo")
							.param("resolution", "720"))
					.andExpect(status().isBadRequest());
		}
		@Test
		void downloadFacebookReturnsOk() throws Exception {
			mockMvc.perform(get("/api/downloadFacebook")
							.param("url", "https://www.facebook.com/watch/?v=123456789"))
					.andExpect(status().isOk());
		}

	}