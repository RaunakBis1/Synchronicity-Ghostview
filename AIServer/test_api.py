from server import app
import io
from PIL import Image

def test_detect():
    # Create a dummy image
    img = Image.new('RGB', (100, 100), color = 'red')
    img_bytes = io.BytesIO()
    img.save(img_bytes, format='JPEG')
    img_bytes.seek(0)
    
    with app.test_client() as client:
        response = client.post('/detect', data={
            'image': (img_bytes, 'test.jpg')
        })
        print("API Response:", response.get_json())

if __name__ == "__main__":
    test_detect()
