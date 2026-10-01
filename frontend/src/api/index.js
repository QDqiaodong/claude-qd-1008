import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

// 后端统一返回 {ok, message, data}；HTTP 400 的业务异常也要把中文提示带出来
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error?.response?.data?.message || error?.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

async function call(promise) {
  const response = await promise
  const body = response.data
  if (!body || body.ok === false) {
    throw new Error((body && body.message) || '请求失败')
  }
  return body.data
}

const params = (o) => ({ params: o })

export default {
  // ---------------- 球洞 ----------------
  listHoles: (p) => call(http.get('/holes', params(p || {}))),
  getHole: (id) => call(http.get(`/holes/${id}`)),
  createHole: (d) => call(http.post('/holes', d)),
  updateHole: (id, d) => call(http.put(`/holes/${id}`, d)),
  setHoleStatus: (id, target) => call(http.post(`/holes/${id}/status`, null, params({ target }))),

  // ---------------- 球员 ----------------
  listPlayers: () => call(http.get('/players')),
  getPlayer: (id) => call(http.get(`/players/${id}`)),
  createPlayer: (d) => call(http.post('/players', d)),
  updatePlayer: (id, d) => call(http.put(`/players/${id}`, d)),

  // ---------------- 开球时间 ----------------
  listTeeTimes: (p) => call(http.get('/tee-times', params(p || {}))),
  getTeeTime: (id) => call(http.get(`/tee-times/${id}`)),
  bookTeeTime: (d) => call(http.post('/tee-times', d)),
  updateTeeTime: (id, d) => call(http.put(`/tee-times/${id}`, d)),
  releaseTeeTime: (id) => call(http.post(`/tee-times/${id}/release`)),

  // ---------------- 赛事 ----------------
  listTournaments: () => call(http.get('/tournaments')),
  getTournament: (id) => call(http.get(`/tournaments/${id}`)),
  createTournament: (d) => call(http.post('/tournaments', d)),
  advanceTournament: (id, target) => call(http.post(`/tournaments/${id}/advance`, null, params({ target }))),

  // ---------------- 排行榜（乐观锁演示） ----------------
  listLeaderboards: (p) => call(http.get('/leaderboards', params(p || {}))),
  getLeaderboard: (id) => call(http.get(`/leaderboards/${id}`)),
  createLeaderboard: (d) => call(http.post('/leaderboards', d)),
  updateLeaderboard: (id, d) => call(http.put(`/leaderboards/${id}`, d)),
  lockAndReadLeaderboard: (id) => call(http.post(`/leaderboards/${id}/lock-and-read`)),

  // ---------------- 成绩 ----------------
  listScores: (p) => call(http.get('/scores', params(p || {}))),
  recordScore: (d) => call(http.post('/scores', d))
}
