import { useState, useEffect, useRef } from 'react'
import AnswerCard from './AnswerCard'
import ChartView from './ChartView'
import DataTable from './DataTable'
import apiClient from '../services/apiClient'
import { useAuth } from '../contexts/AuthContext'
export default function QueryResult({ result, onRecommend }) {
  const { user } = useAuth()
  const [chartData, setChartData] = useState(null)
  const [dataFrame, setDataFrame] = useState(null)
  const [chartLoaded, setChartLoaded] = useState(false)
  const pollingRef = useRef(null)

  useEffect(() => {
    if (!result?.chart_pending || !result?.request_id) return
    const rid = result.request_id
    let attempts = 0
    pollingRef.current = setInterval(async () => {
      attempts++
      if (attempts > 30) {
        clearInterval(pollingRef.current)
        setChartLoaded(true)
        return
      }
      try {
        const { data } = await apiClient.get(`/chart-result/${rid}`)
        if (data.status === 'ready') {
          setChartData(data.ChartData || null)
          setDataFrame(data.DataFrame || null)
          setChartLoaded(true)
          clearInterval(pollingRef.current)
        }
      } catch {}
    }, 2000)
    return () => clearInterval(pollingRef.current)
  }, [result?.request_id, result?.chart_pending])

  if (!result) return null

  const { Answer, Recommendations } = result
  const showChart = chartData || result.ChartData
  const showData = dataFrame || result.DataFrame

  const sqlQuery = result["SQL Query"] || result.sql_query || result.SQL_Query || "";
  let stateName = user?.state_name || 'Central';
  const stateMatch = sqlQuery.match(/plot_state_name\s*(?:ILIKE|=)\s*'([^']+)'/i) || sqlQuery.match(/state_name\s*(?:ILIKE|=)\s*'([^']+)'/i);
  if (stateMatch && stateMatch[1]) {
    stateName = stateMatch[1].split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');
  }

  const sources = [];
  const lowerSql = sqlQuery.toLowerCase();
  if (lowerSql.includes('crop_survey_data_dist')) {
    sources.push(`${stateName} DCS Database`);
  }
  if (lowerSql.includes('farmer_data_dist') || lowerSql.includes('farmer_registry')) {
    sources.push(`${stateName} Farmer Registry Database`);
  }

  return (
    <div className="mb-4">
      <AnswerCard answer={Answer} />
      {showData && <DataTable dataframe={showData} />}
      {showChart && <ChartView chartData={showChart} />}
      {result.chart_pending && !chartLoaded && (
        <p className="text-xs text-gray-400 dark:text-neutral-500 mt-1">Loading chart...</p>
      )}
      {sources.length > 0 && (
        <div className="mt-4 mb-3 p-3 bg-white dark:bg-neutral-800 rounded-lg border border-gray-100 dark:border-neutral-700">
          <p className="text-xs font-semibold text-gray-500 dark:text-neutral-400 mb-2">Data Sources Used:</p>
          <ul className="space-y-1">
            {sources.map((src, i) => (
              <li key={i} className="text-sm text-gray-700 dark:text-gray-300 flex items-center">
                <span className="text-green-500 mr-2 font-bold">•</span>
                {src}
              </li>
            ))}
          </ul>
        </div>
      )}
      {Recommendations && Recommendations.length > 0 && (
        <div className="space-y-2 mb-3">
          <p className="text-xs text-gray-500 dark:text-neutral-400 font-medium">Suggested follow-ups</p>
          {Recommendations.map((q, i) => (
            <button
              key={i}
              onClick={() => onRecommend?.(q)}
              className="w-full text-left bg-white dark:bg-neutral-800 border border-green-200 dark:border-green-800 hover:border-green-400 dark:hover:border-green-500 hover:bg-green-50 dark:hover:bg-neutral-700 rounded-xl px-4 py-3 text-sm text-green-700 dark:text-green-400 transition-colors duration-150"
            >
              {q}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
