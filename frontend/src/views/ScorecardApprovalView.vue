<template>
  <div class="panel">
    <div class="panel-head">
      <span class="panel-title">赛事记分卡 · 提交、确认、争议与裁决</span>
      <span class="panel-desc">已签核才进入正式排行榜；待确认和争议中成绩会阻止赛事收口</span>
    </div>

    <div class="toolbar approval-toolbar">
      <el-select v-model="role" style="width: 130px" @change="reload">
        <el-option label="记分员" value="记分员" />
        <el-option label="球员" value="球员" />
        <el-option label="裁判" value="裁判" />
      </el-select>
      <el-select
        v-if="role === '球员'"
        v-model="actingPlayerId"
        filterable
        placeholder="选择当前球员"
        style="width: 210px"
        @change="reload"
      >
        <el-option v-for="p in players" :key="p.id" :label="`${p.name}（${p.playerNo}）`" :value="p.id" />
      </el-select>
      <el-input v-model="actorName" :placeholder="actorPlaceholder" style="width: 180px" />
      <el-tag :type="statusFilterType" effect="plain">{{ scopeText }}</el-tag>
      <span class="spacer"></span>
      <el-button @click="reload">刷新</el-button>
      <el-button v-if="role === '记分员'" type="primary" @click="openCreateDraft">维护新草稿</el-button>
    </div>

    <el-alert
      class="approval-note"
      type="info"
      show-icon
      :closable="false"
      title="本演示没有登录系统：请通过角色下拉模拟记分员、球员本人和裁判；后端仍会校验球员只能处理本人卡片、裁判只能处理争议卡片。"
    />

    <div class="official-board">
      <div class="sub-title">正式排行榜（仅已签核记分卡）</div>
      <el-table :data="officialRows" border stripe size="small">
        <el-table-column type="index" label="名次" width="70" />
        <el-table-column prop="playerName" label="球员" min-width="120" />
        <el-table-column prop="completedRounds" label="已签核轮次" width="120" />
        <el-table-column prop="totalStrokes" label="总杆" width="90" />
        <el-table-column label="相对标准杆" width="120">
          <template #default="{ row }">
            <el-tag :type="row.totalToPar > 0 ? 'danger' : row.totalToPar < 0 ? 'success' : 'info'">
              {{ formatToPar(row.totalToPar) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!officialRows.length" description="暂无已签核成绩" :image-size="70" />
    </div>

    <el-table :data="scorecards" border stripe class="scorecard-table">
      <el-table-column prop="playerName" label="球员" min-width="110" />
      <el-table-column prop="roundNo" label="轮次" width="80" />
      <el-table-column label="状态" width="125">
        <template #default="{ row }">
          <el-tag :type="scoreStatusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalStrokes" label="当前总杆" width="95" />
      <el-table-column label="计入正式成绩" width="120">
        <template #default="{ row }">
          <el-tag :type="row.official ? 'success' : 'info'">{{ row.official ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="75" />
      <el-table-column prop="lastAction" label="最近操作" width="110" />
      <el-table-column label="操作" min-width="360" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showCard(row)">查看</el-button>
          <el-button size="small" @click="openHistory(row)">轨迹</el-button>
          <template v-if="role === '记分员'">
            <el-button size="small" type="primary" plain :disabled="row.status !== '草稿'" @click="openEditDraft(row)">编辑草稿</el-button>
            <el-button size="small" type="success" plain :disabled="row.status !== '草稿'" @click="submitCard(row)">提交</el-button>
          </template>
          <template v-if="role === '球员' && actingPlayerId === row.playerId">
            <el-button size="small" type="success" plain :disabled="row.status !== '待球员确认'" @click="confirmCard(row)">整卡确认</el-button>
            <el-button size="small" type="warning" plain :disabled="row.status !== '待球员确认'" @click="openDispute(row)">提出争议</el-button>
          </template>
          <el-button v-if="role === '裁判'" size="small" type="danger" plain :disabled="row.status !== '争议中'" @click="openRuling(row)">裁决</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 草稿 -->
    <el-dialog v-model="draftDialog" :title="draftForm.id ? '编辑记分卡草稿' : '维护记分卡草稿'" width="760px">
      <el-alert type="info" :closable="false" show-icon class="dialog-alert"
        title="草稿可暂存空杆数；提交时会校验赛事进行中、应打球洞全部有合法杆数且洞号不重复。提交后这里不能再改写快照。" />
      <el-form :model="draftForm" label-width="90px">
        <el-form-item v-if="!draftForm.id" label="球员">
          <el-select v-model="draftForm.playerId" filterable style="width: 100%">
            <el-option v-for="p in players" :key="p.id" :label="`${p.name}（${p.playerNo}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!draftForm.id" label="轮次">
          <el-input-number v-model="draftForm.roundNo" :min="1" :max="selectedTour?.totalRounds || 1" />
        </el-form-item>
        <div v-for="h in playableHoles" :key="h.id" class="draft-hole-row">
          <div class="hole-label">第 {{ h.holeNo }} 洞 · Par {{ h.par }}</div>
          <el-input-number v-model="draftForm.strokes[h.id]" :min="1" :max="20" placeholder="空=未填" />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="draftDialog = false">取消</el-button>
        <el-button type="primary" :loading="mutating" @click="saveDraft">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 查看明细 -->
    <el-dialog v-model="detailDialog" title="记分卡明细" width="820px">
      <div v-if="selectedCard" class="detail-summary">
        <el-tag :type="scoreStatusType(selectedCard.status)">{{ selectedCard.status }}</el-tag>
        <span>球员：{{ selectedCard.playerName }}</span>
        <span>第 {{ selectedCard.roundNo }} 轮</span>
        <span>版本：{{ selectedCard.version }}</span>
        <span>总杆：{{ selectedCard.totalStrokes }}</span>
      </div>
      <el-table v-if="selectedCard" :data="selectedCard.holes" border size="small">
        <el-table-column prop="holeNo" label="洞号" width="70" />
        <el-table-column prop="par" label="Par" width="70" />
        <el-table-column prop="strokes" label="成绩" width="75" />
        <el-table-column prop="proposedStrokes" label="争议杆数" width="90" />
        <el-table-column prop="disputeReason" label="争议理由" min-width="150" />
        <el-table-column label="裁决" min-width="170">
          <template #default="{ row }">
            <div v-if="row.rulingDecision">
              <el-tag size="small" :type="row.rulingDecision === '修正' ? 'warning' : 'info'">{{ row.rulingDecision }}</el-tag>
              <div class="ruling-note">{{ row.rulingNote }}（{{ row.ruledBy }}）</div>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 争议 -->
    <el-dialog v-model="disputeDialog" title="球员逐洞提出争议" width="820px">
      <el-alert type="warning" :closable="false" show-icon class="dialog-alert"
        title="已确认的整卡不能撤回；争议提交后整张卡暂不计入正式排行榜，裁判裁决后会重新回到待确认。" />
      <el-table :data="disputeRows" border size="small">
        <el-table-column label="选择" width="55">
          <template #default="{ row }"><el-checkbox v-model="row.checked" /></template>
        </el-table-column>
        <el-table-column prop="holeNo" label="洞号" width="70" />
        <el-table-column prop="par" label="Par" width="65" />
        <el-table-column prop="strokes" label="原成绩" width="80" />
        <el-table-column label="我认为杆数" width="135">
          <template #default="{ row }"><el-input-number v-model="row.proposed" :min="1" :max="20" :disabled="!row.checked" /></template>
        </el-table-column>
        <el-table-column label="争议理由" min-width="220">
          <template #default="{ row }"><el-input v-model="row.reason" :disabled="!row.checked" placeholder="请填写具体原因" /></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="disputeDialog = false">取消</el-button>
        <el-button type="warning" :loading="mutating" @click="submitDispute">提交争议</el-button>
      </template>
    </el-dialog>

    <!-- 裁决 -->
    <el-dialog v-model="rulingDialog" title="裁判处理争议" width="820px">
      <el-form label-width="100px">
        <el-form-item label="裁决方式">
          <el-radio-group v-model="rulingForm.decision">
            <el-radio label="维持">维持原成绩</el-radio>
            <el-radio label="修正">给出修正杆数</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-table :data="rulingRows" border size="small">
          <el-table-column prop="holeNo" label="洞号" width="70" />
          <el-table-column prop="par" label="Par" width="65" />
          <el-table-column prop="strokes" label="原成绩" width="80" />
          <el-table-column prop="proposedStrokes" label="球员提案" width="90" />
          <el-table-column prop="disputeReason" label="争议理由" min-width="170" />
          <el-table-column label="修正杆数" width="135">
            <template #default="{ row }">
              <el-input-number v-model="row.corrected" :min="1" :max="20" :disabled="rulingForm.decision !== '修正'" />
            </template>
          </el-table-column>
        </el-table>
        <el-form-item label="裁决说明" class="ruling-text">
          <el-input v-model="rulingForm.note" type="textarea" :rows="3" placeholder="必须说明维持或修正依据" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rulingDialog = false">取消</el-button>
        <el-button type="danger" :loading="mutating" @click="submitRuling">提交裁决</el-button>
      </template>
    </el-dialog>

    <!-- 历史轨迹 -->
    <el-dialog v-model="historyDialog" title="不可变签核轨迹" width="860px">
      <el-timeline>
        <el-timeline-item
          v-for="v in versions"
          :key="v.id"
          :type="v.snapshotType === 'BEFORE' ? 'warning' : 'success'"
          :timestamp="`${v.snapshotType} · 主版本 ${v.cardVersion} · ${formatTime(v.createdAt)}`"
        >
          <el-card shadow="never">
            <div class="version-head">
              <strong>{{ v.action }}</strong>
              <el-tag size="small">{{ v.status }}</el-tag>
              <span>{{ v.actorRole }}：{{ v.actorName || '未署名' }}</span>
            </div>
            <div class="version-note">{{ v.note }}</div>
            <el-table :data="v.holes" border size="small" class="version-table">
              <el-table-column prop="holeNo" label="洞" width="55" />
              <el-table-column prop="strokes" label="杆" width="55" />
              <el-table-column prop="proposedStrokes" label="提案" width="60" />
              <el-table-column prop="disputeReason" label="争议" min-width="120" />
              <el-table-column prop="rulingDecision" label="裁决" width="65" />
              <el-table-column prop="rulingNote" label="裁决说明" min-width="140" />
            </el-table>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import api from '../api'

const props = defineProps({
  tournamentId: { type: Number, default: null },
  selectedTournament: { type: Object, default: null },
  players: { type: Array, default: () => [] },
  holes: { type: Array, default: () => [] }
})

const emit = defineEmits(['changed'])

const role = ref('记分员')
const actingPlayerId = ref(null)
const actorName = ref('')
const scorecards = ref([])
const officialRows = ref([])
const mutating = ref(false)

const draftDialog = ref(false)
const detailDialog = ref(false)
const disputeDialog = ref(false)
const rulingDialog = ref(false)
const historyDialog = ref(false)
const selectedCard = ref(null)
const versions = ref([])
const disputeRows = ref([])
const rulingRows = ref([])

const draftForm = ref(blankDraft())

const statusFilterType = computed(() => {
  if (role.value === '裁判') return 'danger'
  if (role.value === '球员') return 'warning'
  return 'primary'
})
const scopeText = computed(() => {
  if (role.value === '裁判') return '裁判只列争议中记分卡'
  if (role.value === '球员') return '球员只列本人记分卡'
  return '记分员维护草稿并提交'
})
const actorPlaceholder = computed(() => {
  if (role.value === '裁判') return '裁判姓名（可选）'
  if (role.value === '球员') return '球员署名（可选）'
  return '记分员姓名（可选）'
})
const playableHoles = computed(() => props.holes.filter(h => h.status === '开放'))

function blankDraft() {
  return { id: null, playerId: null, roundNo: 1, expectedVersion: null, strokes: {} }
}

async function reload() {
  if (!props.tournamentId) {
    scorecards.value = []
    officialRows.value = []
    return
  }
  const query = { tournamentId: props.tournamentId, role: role.value }
  if (role.value === '球员') {
    if (!actingPlayerId.value && props.players.length) actingPlayerId.value = props.players[0].id
    query.playerId = actingPlayerId.value
  }
  const [cards, official] = await Promise.all([
    api.listScorecards(query),
    api.officialLeaderboard(props.tournamentId)
  ])
  scorecards.value = cards || []
  officialRows.value = official || []
}

async function withMutation(fn) {
  mutating.value = true
  try {
    const result = await fn()
    await reload()
    emit('changed')
    return result
  } finally {
    mutating.value = false
  }
}

async function handleConflict(e, cardId) {
  if (e.status === 409 && e.data) {
    selectedCard.value = e.data
    const idx = scorecards.value.findIndex(c => c.id === (cardId || e.data?.id))
    if (idx >= 0) scorecards.value.splice(idx, 1, e.data)
  }
}

function openCreateDraft() {
  draftForm.value = blankDraft()
  draftForm.value.playerId = actingPlayerId.value || props.players[0]?.id || null
  draftForm.value.roundNo = 1
  draftDialog.value = true
}

function openEditDraft(card) {
  draftForm.value = {
    id: card.id,
    playerId: card.playerId,
    roundNo: card.roundNo,
    expectedVersion: card.version,
    strokes: Object.fromEntries(card.holes.map(h => [h.holeId, h.strokes]))
  }
  draftDialog.value = true
}

async function saveDraft() {
  if (!draftForm.value.playerId) return showLocalError('请选择球员')
  try {
    const payload = {
      tournamentId: props.tournamentId,
      playerId: draftForm.value.playerId,
      roundNo: Number(draftForm.value.roundNo),
      expectedVersion: draftForm.value.expectedVersion,
      actorName: actorName.value,
      holes: playableHoles.value.map(h => ({ holeId: h.id, strokes: draftForm.value.strokes[h.id] || null }))
    }
    const saved = await withMutation(() => api.saveScorecardDraft(payload))
    draftDialog.value = false
    selectedCard.value = saved
  } catch (e) { showLocalError(e.message) }
}

async function submitCard(card) {
  try {
    await withMutation(() => api.submitScorecard(card.id, {
      expectedVersion: card.version,
      actorName: actorName.value
    }))
  } catch (e) {
    await handleConflict(e, card.id)
    showLocalError(e.message)
  }
}

async function confirmCard(card) {
  try {
    await withMutation(() => api.confirmScorecard(card.id, actingPlayerId.value, {
      expectedVersion: card.version,
      actorName: actorName.value
    }))
  } catch (e) {
    await handleConflict(e, card.id)
    showLocalError(e.message)
  }
}

function openDispute(card) {
  selectedCard.value = card
  disputeRows.value = card.holes.map(h => ({
    holeId: h.holeId,
    holeNo: h.holeNo,
    par: h.par,
    strokes: h.strokes,
    checked: false,
    proposed: h.strokes,
    reason: ''
  }))
  disputeDialog.value = true
}

async function submitDispute() {
  const picked = disputeRows.value.filter(r => r.checked)
  if (!picked.length) return showLocalError('请至少勾选一个争议球洞')
  if (picked.some(r => !r.proposed || r.proposed < 1)) return showLocalError('争议杆数必须 ≥ 1')
  if (picked.some(r => !r.reason.trim())) return showLocalError('每个争议球洞都要填写理由')
  try {
    await withMutation(() => api.disputeScorecard(selectedCard.value.id, actingPlayerId.value, {
      expectedVersion: selectedCard.value.version,
      actorName: actorName.value,
      holes: picked.map(r => ({ holeNo: r.holeNo, proposedStrokes: Number(r.proposed), reason: r.reason.trim() }))
    }))
    disputeDialog.value = false
  } catch (e) {
    await handleConflict(e, selectedCard.value.id)
    showLocalError(e.message)
  }
}

function openRuling(card) {
  selectedCard.value = card
  rulingRows.value = card.holes.filter(h => h.disputeReason).map(h => ({ ...h, corrected: h.strokes }))
  rulingForm.value = { decision: '维持', note: '', expectedVersion: card.version }
  rulingDialog.value = true
}

const rulingForm = ref({ decision: '维持', note: '', expectedVersion: null })

async function submitRuling() {
  if (!rulingForm.value.note.trim()) return showLocalError('裁判必须填写裁决说明')
  if (rulingForm.value.decision === '修正' && rulingRows.value.some(r => !r.corrected || r.corrected < 1)) {
    return showLocalError('每个争议洞都必须给出合法修正杆数')
  }
  try {
    await withMutation(() => api.ruleScorecard(selectedCard.value.id, {
      expectedVersion: selectedCard.value.version,
      refereeName: actorName.value,
      decision: rulingForm.value.decision,
      note: rulingForm.value.note.trim(),
      correctedHoles: rulingForm.value.decision === '修正'
        ? rulingRows.value.map(r => ({ holeNo: r.holeNo, correctedStrokes: Number(r.corrected) }))
        : []
    }))
    rulingDialog.value = false
  } catch (e) {
    await handleConflict(e, selectedCard.value.id)
    showLocalError(e.message)
  }
}

async function showCard(card) {
  try {
    selectedCard.value = await api.getScorecard(card.id, {
      role: role.value,
      playerId: role.value === '球员' ? actingPlayerId.value : undefined
    })
    detailDialog.value = true
  } catch (e) { showLocalError(e.message) }
}

async function openHistory(card) {
  try {
    versions.value = await api.scorecardHistory(card.id, {
      role: role.value,
      playerId: role.value === '球员' ? actingPlayerId.value : undefined
    })
    historyDialog.value = true
  } catch (e) { showLocalError(e.message) }
}

function showLocalError(message) {
  window.alert(message)
}

function scoreStatusType(status) {
  if (status === '已签核') return 'success'
  if (status === '争议中') return 'danger'
  if (status === '待球员确认') return 'warning'
  return 'info'
}
function formatToPar(value) {
  if (value === 0) return 'E'
  return value > 0 ? `+${value}` : `${value}`
}
function formatTime(value) {
  return value ? value.replace('T', ' ').substring(0, 19) : ''
}

watch(() => props.tournamentId, reload, { immediate: true })
watch(actingPlayerId, () => { if (role.value === '球员') reload() })
watch(role, reload)
watch(() => props.players, () => {
  if (!actingPlayerId.value && props.players.length) actingPlayerId.value = props.players[0].id
}, { immediate: true })
</script>

<style scoped>
.approval-toolbar { gap: 10px; }
.approval-note { margin: 10px 0 14px; }
.official-board { margin-bottom: 16px; }
.sub-title { font-weight: 700; color: #16675d; margin: 6px 0 8px; }
.scorecard-table { width: 100%; }
.draft-hole-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #edf4f2;
}
.hole-label { color: #47615c; font-weight: 600; }
.dialog-alert { margin-bottom: 14px; }
.detail-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  color: #47615c;
}
.ruling-note { margin-top: 4px; color: #6b7f7a; line-height: 1.4; }
.ruling-text { margin-top: 14px; }
.version-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.version-note { color: #4e6661; margin-bottom: 8px; }
.version-table { margin-top: 6px; }
</style>
