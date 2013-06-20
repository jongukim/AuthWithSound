#include <jni.h>
#include <fftw3.h>

JNIEXPORT jdoubleArray JNICALL Java_kr_ac_ajou_dv_authwithsound_FftHelper_fftw(
	JNIEnv *env,
	jobject obj,
	jdoubleArray chunkArray,
	jint size) {
		jdoubleArray resultArray;
		jdouble *chunk, *result;
		double *in;
		fftw_complex *out;
		fftw_plan p;
		int i, k;
		int len = size / 2 + 1;

		chunk = (*env)->GetDoubleArrayElements(env, chunkArray, 0);		

		resultArray = (*env)->NewDoubleArray(env, size + 2);
		if(resultArray == NULL) return NULL;
		result = (*env)->GetDoubleArrayElements(env, resultArray, 0);

		in = (double *) fftw_malloc(sizeof(double) * size);
		out = (fftw_complex *) fftw_malloc(sizeof(fftw_complex) * len);
		p = fftw_plan_dft_r2c_1d(size, chunk, out, FFTW_ESTIMATE);
		
		for(i = 0; i < size; i++)
			in[i] = chunk[i];

		fftw_execute(p);
		
		for(i = 0, k = 0; i < len; i++) {
			result[k++] = out[i][0];
			result[k++] = out[i][1];
		}
		fftw_destroy_plan(p);
		fftw_free(in);
		fftw_free(out);

		(*env)->ReleaseDoubleArrayElements(env, chunkArray, chunk, 0);
		(*env)->ReleaseDoubleArrayElements(env, resultArray, result, 0);
		return resultArray;
}

