import { useState, useEffect } from 'react'

export default function QueryLoader() {
  const [timeElapsed, setTimeElapsed] = useState(0)
  const [dots, setDots] = useState('')

  useEffect(() => {
    // Timer to track seconds elapsed
    const timerInterval = setInterval(() => {
      setTimeElapsed(prev => prev + 1)
    }, 1000)

    // Timer for the animated dots (changes every 400ms)
    const dotsInterval = setInterval(() => {
      setDots(prev => {
        if (prev === '...') return ''
        return prev + '.'
      })
    }, 400)

    return () => {
      clearInterval(timerInterval)
      clearInterval(dotsInterval)
    }
  }, [])

  let text = 'Analyzing Query'
  if (timeElapsed >= 5 && timeElapsed <= 12) {
    text = 'Retrieving Context'
  } else if ((timeElapsed >= 13 && timeElapsed < 30) || timeElapsed > 32) {
    text = 'Generating Response'
  } else if (timeElapsed >= 30 && timeElapsed <= 32) {
    text = 'Retrying'
  }

  return (
    <div className="flex flex-col gap-2 w-full max-w-3xl">
      <div className="flex items-center space-x-3 p-4 bg-white dark:bg-neutral-800 rounded-2xl rounded-bl-sm border border-gray-100 dark:border-neutral-700 w-max max-w-sm">
        <div className="flex space-x-1">
          <div className="w-2 h-2 bg-green-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
          <div className="w-2 h-2 bg-green-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
          <div className="w-2 h-2 bg-green-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
        </div>
        <div className="text-sm font-medium text-gray-700 dark:text-gray-300 w-40 text-left">
          {text}{dots}
        </div>
      </div>
      
      {text === 'Generating Response' && (
        <div className="w-full animate-pulse mt-2">
          <div className="bg-white dark:bg-neutral-800 border border-gray-200 dark:border-neutral-700 rounded-xl p-5 mb-3">
            <div className="h-4 bg-gray-200 dark:bg-neutral-700 rounded w-3/4 mb-4"></div>
            <div className="h-4 bg-gray-200 dark:bg-neutral-700 rounded w-full mb-4"></div>
            <div className="h-4 bg-gray-200 dark:bg-neutral-700 rounded w-5/6 mb-4"></div>
            <div className="h-4 bg-gray-200 dark:bg-neutral-700 rounded w-1/2"></div>
          </div>
        </div>
      )}
    </div>
  )
}
