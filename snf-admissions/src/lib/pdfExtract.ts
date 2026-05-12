import * as pdfjs from 'pdfjs-dist'

// Point the worker at the bundled worker file
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.mjs',
  import.meta.url,
).toString()

export async function extractTextFromPDF(file: File): Promise<string> {
  const arrayBuffer = await file.arrayBuffer()
  const typedArray = new Uint8Array(arrayBuffer)

  const pdf = await pdfjs.getDocument({ data: typedArray }).promise
  const texts: string[] = []

  for (let i = 1; i <= pdf.numPages; i++) {
    const page = await pdf.getPage(i)
    const content = await page.getTextContent()
    const pageText = content.items
      .map((item) => ('str' in item ? item.str : ''))
      .join(' ')
    texts.push(pageText)
  }

  return texts.join('\n\n').slice(0, 14000) // cap to avoid token overflow
}
